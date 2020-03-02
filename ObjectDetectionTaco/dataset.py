import json
import numpy as np
import pandas as pd
from os import listdir, mkdir, rmdir
from os.path import isfile, join, isdir, exists
from sklearn.model_selection import train_test_split
import pycococreatortools as creator
from pycocotools.coco import COCO, maskUtils
import cv2
import numpy as np
from imgaug import augmenters as iaa
import imgaug as ia


class Dataset:
    def __init__(self, root_path):
        self.root_path = root_path
        self.dataset_path = root_path + "/data"
        self.ann_file_path = self.dataset_path + "/annotations.json"
        self.coco = COCO(self.ann_file_path)

        # read dataset
        with open(self.ann_file_path, 'r') as f:
            self.dataset = json.loads(f.read())

        self.categories = self.dataset['categories']  # leave as is
        self.anns = self.dataset['annotations']
        self.imgs = self.dataset['images']

        # next free img_id and ann_id
        self.img_id = len(self.imgs)
        self.ann_id = len(self.anns)
        self.last_cat = 0

        self.file_ids = dict()
        self.ids_file = dict()

        for img in self.imgs:
            self.file_ids[img['file_name']] = img['id']
            self.ids_file[img['id']] = img['file_name']

    def cat_to_super_cat(self):
        """Map categories into supercategories

        Returns:
            map - cat_id: super_cat_id
        """
        # Map categories and super categories
        super_cat_last_name = self.categories[0]['supercategory']

        nr_super_cats = 1
        cat_to_super_cat = {}

        for i, cat_it in enumerate(self.categories):
            super_cat_name = cat_it['supercategory']
            # Adding new supercat
            if super_cat_name != super_cat_last_name:
                super_cat_last_name = super_cat_name
                nr_super_cats += 1
            cat_to_super_cat[i] = nr_super_cats - 1

        return cat_to_super_cat

    def super_cat_to_cat(self):
        """Map categories into supercategories

        Returns:
            map - cat_id: super_cat_id
        """

        # Map categories and super categories
        super_cat_last_name = self.categories[0]['supercategory']

        nr_super_cats = 0
        super_cat_2_cat = {}

        cats = []
        for i, cat_it in enumerate(self.categories):
            super_cat_name = cat_it['supercategory']
            # Adding new supercat
            if super_cat_name != super_cat_last_name:
                super_cat_2_cat[nr_super_cats] = cats
                cats = [i]
                nr_super_cats += 1
                super_cat_last_name = super_cat_name
            else:
                cats.append(i)
        super_cat_2_cat[nr_super_cats] = cats
        return super_cat_2_cat

    def images_file_path(self):
        dataset_files = []
        dataset_dirs = [f for f in listdir(
            self.dataset_path) if isdir(join(self.dataset_path, f))]
        for dir_name in dataset_dirs:
            path = self.dataset_path + "/" + dir_name
            for f in listdir(path):
                if isfile(join(path, f)) and not f.endswith(".txt"):
                    dataset_files.append(dir_name + '/' + f)
        return dataset_files

    def split_data_train_and_test(self, train_file, test_file):
        file_train = self.root_path + train_file
        file_test = self.root_path + test_file

        dataset_files = self.images_file_path()

        X_train, X_test = train_test_split(
            dataset_files, test_size=0.2, random_state=0)

        with open(file_train, "w") as file_stream:
            for file_name in X_train:
                file_name = self.dataset_path + "/" + file_name
                file_stream.write(file_name)
                file_stream.write("\n")

        with open(file_test, "w") as file_stream:
            for file_name in X_test:
                file_name = self.dataset_path + "/" + file_name
                file_stream.write(file_name)
                file_stream.write("\n")

    def compute_relative_coordonates(self, x, y, w, h, imW, imH):
        """
          - compute relative coordonates for a bbox
          - needs a point (x,y) up left from bbox
      Arguments:
          x int -- x ax
          y int -- y ax
          w int -- width of bbox
          h int -- height of bbox
          imW int -- image width
          imH int -- image height
      Returns:
           x_center
           y_center
           width
           height
          """
        x_center = (x + w / 2) / imW
        y_center = (y + h / 2) / imH
        width = w / imW
        height = h / imH
        return x_center, y_center, width, height

    def create_label_file(self, file_name):
        dataset_files = self.images_file_path()

        for image_file_path in dataset_files:
            text_filepath = self.dataset_path + \
                '/' + image_file_path[:-3] + "txt"
            print(text_filepath)

            img_id = self.file_ids[image_file_path]
            if img_id == -1:
                continue

            annIds = self.coco.getAnnIds(
                imgIds=img_id, catIds=[], iscrowd=None)
            anns_sel = self.coco.loadAnns(annIds)

            with open(text_filepath, "w+") as file_stream:
                for image_info in self.anns:
                    image_id = image_info["image_id"]
                    im = self.coco.imgs[image_id]

                    [x, y, w, h] = image_info['bbox']
                    # Category id
                    category_id = self.cat_to_super_cat()[
                        image_info["category_id"]]

                    # Compute centers
                    x_center, y_center, width, height = self.compute_relative_coordonates(
                        x, y, w, h, im["width"], im['height'])

                    # Write coordonates and class type to file
                    line = str(category_id) + " " + str(x_center) + " " + \
                        str(y_center) + " " + str(width) + \
                        " " + str(height) + "\n"
                    file_stream.write(line)

    def activator_masks(self, images, augmenter, parents, default):
        if augmenter.name in ["Blur", "AWGN", "Add", "Multiply"]:
            return False
        else:
            # default value for all other augmenters
            return default

    def apply_mask(self, image, mask):
        """
        Apply mask on image.
        Overlap image and mask.
        Arguments:
            image {[type]} -- [description]
            mask {[type]} -- [description]

        Returns:
            image -- an image with mask applied
        """
        seg_mask = mask.sum(axis=2).astype(np.bool8)
        mask_image = []
        for j in range(3):
            mask_image.append(np.multiply(image[:, :, j], seg_mask))
        mask_image = np.stack(np.array(mask_image), axis=2)
        return mask_image

    def ann_to_mask(self, annotation, height, width):
        """
        Create mask from segmentation.
        Arguments:
            height int -- image height
            width int -- image width

        Returns:
            mask -- mask of an image
        """
        segm = annotation['segmentation']
        if isinstance(segm, list):
            # encode the mask into a single run-length encoded mask
            mask_encodings = maskUtils.frPyObjects(segm, height, width)
            mask_encoding = maskUtils.merge(mask_encodings)
        else:
            # already encoded
            mask_encoding = segm
        mask = maskUtils.decode(mask_encoding)
        return mask

    def load_masks(self, image_id):
        """Load instance masks for the given image.

        Different datasets use different ways to store masks. This
        function converts the different mask format to one format
        in the form of a bitmap [height, width, instances].

        Returns:
        masks: A bool array of shape [height, width, instance count] with
            one mask per instance.
        class_ids: a 1D array of class IDs of the instance masks.
        """

        instance_masks = []
        class_ids = []
        anns = self.coco.loadAnns(self.coco.getAnnIds(
            imgIds=[image_id], catIds=self.coco.getCatIds(), iscrowd=None))
        # Build mask of shape [height, width, instance_count] and list
        # of class IDs that correspond to each channel of the mask.
        for annotation in anns:
            class_id = annotation["category_id"]
            im = self.coco.imgs[image_id]
            m = self.ann_to_mask(annotation, im["height"],
                                 im["width"])
            # Some objects are so small that they're less than 1 pixel area
            # and end up rounded out. Skip those objects.
            if m.max() < 1:
                continue
            instance_masks.append(m)
            class_ids.append(class_id)

        # Pack instance masks into an array
        if len(class_ids) != 0:
            mask = np.stack(instance_masks, axis=2).astype(np.bool)
            class_ids = np.array(class_ids, dtype=np.int32)
            return mask, class_ids

    def load_image(self, image_id):
        """Load the specified image and return as a [H,W,3] Numpy array."""
        image = cv2.imread(self.dataset_path + "/" + self.ids_file[image_id])
        img_shape = np.shape(image)
        # If has an alpha channel, remove it for consistency
        if img_shape[-1] == 4:
            image = image[..., :3]
        return np.array(image)

    def create_bbox(self, mask):
        """Create bbox for mask

        Arguments:
            mask array-- image mask

        Returns:
            x, y, w, h-- bounding box coordonates
        """
        img = mask.astype(np.int8)
        rows = np.any(img, axis=1)
        cols = np.any(img, axis=0)
        ymin, ymax = np.where(rows)[0][[0, -1]]
        xmin, xmax = np.where(cols)[0][[0, -1]]
        w = xmax - xmin
        h = ymax - ymin
        return xmin, ymin, w, h

    def create_pipeline(self):
        """
        Augumentation pipeline
        Returns:
            seq -- pipeline
        """
        seq = iaa.Sequential([
            iaa.AdditiveGaussianNoise(scale=0.01 * 255, name="AWGN"),
            iaa.GaussianBlur(sigma=(0.0, 3.0), name="Blur"),
            # iaa.Dropout([0.0, 0.05], name='Dropout'), # drop 0-5% of all pixels
            iaa.Fliplr(0.5),
            iaa.Add((-20, 20), name="Add"),
            iaa.Multiply((0.8, 1.2), name="Multiply"),
            iaa.Affine(scale=(0.8, 2.0)),
            iaa.Affine(translate_percent={"x": (-0.2, 0.2), "y": (-0.2, 0.2)}),
            iaa.Affine(rotate=(-45, 45)),  # rotate by -45 to 45 degrees
        ], random_order=True)
        return seq

    def augment_image(self, image, super_class, masks, ann_cats, nr_augmentations):
        hooks_masks = ia.HooksImages(activator=self.activator_masks)
        seq = self.create_pipeline()
        imgs_info = []
        anns_info = []
        for i in range(nr_augmentations):
            seq_det = seq.to_deterministic()
            image_augmented = seq_det.augment_image(image)
            mask_augmented = seq_det.augment_image(
                masks.astype(np.uint8), hooks=hooks_masks)
            # TODO: remove hardcoded path initialize new folder in constructor
            image_info, ann_info = self.get_coco_info(
                self.dataset_path + "/augm", image_augmented, mask_augmented, ann_cats)
            imgs_info.append(image_info)
            anns_info.extend(ann_info)

            path = self.dataset_path + "/" + image_info['file_name']
            cv2.imwrite(path, image_augmented)
        return imgs_info, anns_info

    def count_super_cat(self, nr_super_cats):
        cat_ids_2_supercat_ids = self.cat_to_super_cat()

        # Count annotations
        super_cat_histogram = np.zeros(nr_super_cats, dtype=int)
        for ann in self.anns:
            cat_id = ann['category_id']
            super_cat_histogram[cat_ids_2_supercat_ids[cat_id]] += 1

        return super_cat_histogram

    def compute_nr_aug(self, nr_elems, max_cnt):
        init_augm = (max_cnt - nr_elems) // nr_elems
        remainder = (max_cnt - nr_elems) % nr_elems
        return init_augm, remainder

    def imgs_per_cats(self, cat_list):
        image_list = []
        for cat in cat_list:
            image_list += self.coco.getImgIds(catIds=cat)

        return list(set(image_list))

    def restore_session(self):
        log_file = self.dataset_path + "/last_save.log"
        if exists(log_file):
            with open(log_file) as fs:
                last_line = fs.readlines()[-1]
                last_line = last_line.strip("\n").split(",")
                self.last_cat = last_line[0]
                self.img_id = last_line[1]
                self.ann_id = last_line[2]

    def generate_filepath(self, generated_dir, img_id):
        if not exists(generated_dir):
            mkdir(generated_dir)
            print("Dir:{} does not exist, creating...".format(generated_dir))

        img_filename = 'gen_{}.jpg'.format(img_id)
        data_dir = generated_dir.split('/')[-1]
        return data_dir + '/' + img_filename

    def get_coco_info(self, generated_dir, image, masks, ann_cats):
        img_filepath = self.generate_filepath(generated_dir, self.img_id)

        image_info = creator.create_image_info(
            self.img_id, img_filepath, image.shape)

        anns_info = []
        for i in range(masks.shape[2]):
            if(np.any(masks[:, :, i])):
                ann_info = creator.create_annotation_info(
                    self.ann_id, self.img_id, ann_cats[i], masks[:, :, i], image.shape)
                if ann_info is not None:
                    anns_info.append(ann_info)
                    self.ann_id += 1

        self.img_id += 1
        return image_info, anns_info

    def generate_augmented_datas(self):
        super_to_cat = self.super_cat_to_cat()
        nr_super_cats = len(super_to_cat)
        super_cat_histogram = self.count_super_cat(nr_super_cats)
        max_cnt = np.max(super_cat_histogram)

        for i in range(self.last_cat, nr_super_cats):
            img_info = []
            ann_info = []
            image_ids = self.imgs_per_cats(super_to_cat[i])
            init_augm, remainder = self.compute_nr_aug(
                super_cat_histogram[i], max_cnt)
            if init_augm != 0:
                for j, im_id in enumerate(image_ids):
                    masks, ann_cats = self.load_masks(im_id)
                    image = self.load_image(im_id)
                    print("Supercategory: {}/{} Current image: {}/{} augs: {}, image: {}"
                          .format(i + 1, nr_super_cats, j + 1, len(image_ids),
                                  init_augm, self.ids_file[im_id]))
                    img_i, ann_i = self.augment_image(image, super_class=i, masks=masks,
                                                      ann_cats=ann_cats, nr_augmentations=init_augm)
                    img_info.extend(img_i)
                    ann_info.extend(ann_i)
                    # while remainder:
                    #     im_id = np.random(0, nr_super_cats)
                    #     masks, ann_cats = self.load_masks(im_id)
                    #     image = self.load_image(im_id)
                    #     self.augment_image(image, super_class=i,
                    #                        mask=masks, nr_augmentations=1)
                    #     remainder -= 1
            else:
                print("Supercategory: {}/{} No augs to be done."
                      .format(i + 1, nr_super_cats))
            print("Finished supercategory {}, saving...".format(i))
            self.create_annotation_file(i, img_info, ann_info)
            print("Progress saved.")

    def create_annotation_file(self, current_super_cat, img_info, ann_info):
        ann_file = self.dataset_path + "/ann_aug.json"
        data = {}
        if exists(ann_file):
            with open(ann_file) as fs:
                data = json.loads(fs.read())
                data["images"].extend(img_info)
                data["annotations"].extend(ann_info)
        else:
            data = {"info": self.dataset["info"], "images": self.imgs,
                    "annotations": self.anns, "scene_annotations":
                    self.dataset["scene_annotations"], "licenses": [],
                    "categories": self.categories,
                    "scene_categories": self.dataset["scene_categories"]}
        with open(ann_file, 'w') as f:
            json.dump(data, f, ensure_ascii=False)
        with open(self.dataset_path + '/last_save.log', 'a+') as f:
            f.write("{}, {}, {}\n".format(
                current_super_cat, self.img_id, self.ann_id))
