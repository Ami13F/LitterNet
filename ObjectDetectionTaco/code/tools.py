import os
import numpy as np


def get_dataset_files(dataset_path, dir_path=None):
    '''
    Get a list with file paths, from a directory.
    - dir_path: string, a path to directory
    '''
    if dir_path is None:
        dir_path = dataset_path
        
    dataset_files = []
    dataset_dirs = [f for f in os.listdir(
        dir_path) if os.path.isdir(os.path.join(dir_path, f))]
    if not dataset_dirs:
        dataset_dirs.append(dir_path.split("/")[-1])

    for dir_name in dataset_dirs:
        path = dataset_path + "/" + dir_name
        for f in os.listdir(path):
            if os.path.isfile(os.path.join(path, f)) and not f.endswith(".txt"):
                dataset_files.append(dir_name + '/' + f)
    return dataset_files


def img_paths(id_to_file, img_ids):
    dataset_files = []
    for im_id in img_ids:
        dataset_files.append(id_to_file[im_id])
    return dataset_files


def save_data(root_path, img_paths, destfile, dataset_path=None):
    '''
    Write in destfile absolute image paths.
    - img_paths: list with image paths "dir/filename"
    - desfile: filename
    '''

    destpath = root_path + '/' + destfile
    print("saving data to {} file...".format(destfile))
    with open(destpath, "w") as file_stream:
        for filepath in img_paths:
            if dataset_path: # if path is not relative/absolute
                filepath = dataset_path + '/' + filepath
            file_stream.write(filepath)
            file_stream.write("\n")
    print("successfully saved to {}.".format(destfile))


def compute_relative_coordinates(x, y, w, h, imW, imH):
    '''
    - relative coordinates from pixel-based ones
    - given (x,y) upper right bbox corner
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
    '''
    x_center = (x + w / 2) / imW        
    y_center = (y + h / 2) / imH
    width = w / imW
    height = h / imH
    return x_center, y_center, width, height


def create_label_file(coco, dataset_path, file_to_id, img_paths):
    '''
    '''
    for i, image_file_path in enumerate(img_paths):
        text_filepath = dataset_path + \
            '/' + image_file_path[:-3] + "txt"
        print("Saving {}/{} - {}...".format(
            i + 1,len(img_paths), image_file_path))

        # assert(image_file_path in file_to_id.keys())
        if image_file_path not in file_to_id.keys():
            continue
        img_id = file_to_id[image_file_path]

        annIds = coco.getAnnIds(
            imgIds=img_id, catIds=[], iscrowd=None)
        anns_sel = coco.loadAnns(annIds)

        with open(text_filepath, "w") as file_stream:
            for image_info in anns_sel:
                image_id = image_info["image_id"]

                [x, y, w, h] = image_info['bbox']
                im = coco.imgs[image_id]
                imW = im['width']
                imH = im['height']

                # Compute relative coords
                x_center, y_center, width, height = compute_relative_coordinates(
                    x, y, w, h, imW, imH)

                # Write coordinates and class type to file
                # in taco 0 class is background, objects start from 1
                # darknet starts from 0
                if image_info["category_id"] < 1:
                    continue
                line = str(image_info["category_id"] - 1) + " " + str(x_center) + " " + \
                    str(y_center) + " " + str(width) + \
                    " " + str(height) + "\n"
                file_stream.write(line)


def apply_mask(image, mask):
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
