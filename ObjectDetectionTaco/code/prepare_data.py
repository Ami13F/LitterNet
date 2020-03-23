import os
import sys
import random
import numpy as np

root_path = "/bigdata/users-data/fair/darknet"
module_path = os.path.abspath(os.path.join(root_path + "/code"))

if module_path not in sys.path:
    sys.path.append(module_path)

from dataset import Dataset

def inv_class_prob(data):
    # inverse soft-max
    # normalize data
    count = data.shape[0]
    maxi = np.max(data) * np.ones(count)
    mini = np.min(data) * np.ones(count)
    norm_data = (data - mini) / (maxi - mini)

    # get probability vector

    sumexp = np.sum(np.exp(norm_data)) # exponents sum
    prob = np.exp(norm_data) / sumexp # soft-max

    # get inverse-prob vector
    maxi = 1
    inv_sum = np.sum(maxi * np.ones(count) - prob)
    invprob = (maxi * np.ones(count) - prob) / inv_sum # inverse soft-max
    return invprob

def sample_data(class_freq, stddev, num_ann, pprob=0.0, **kwargs):
    """
        sample data from a distribution of images
        exclude_init_cat - list
        exclude_aug_cat - list
        ids_include - list
        ids_exclude - list
        pprob - list
    """

    init_cats = set()
    aug_cats = set()
    img_ids = set()
    exclude_ids = set()
    skip_cats = set()
    
    if "exclude_init_cat" in kwargs.keys():
        for scat in kwargs["exclude_init_cat"]:
            init_cats |= set(data.scat2cat[scat])
    if "exclude_aug_cat" in kwargs.keys():
        for scat in kwargs["exclude_aug_cat"]:
            aug_cats |= set(data.scat2cat[scat])
    if "ids_include" in kwargs.keys():
        img_ids = set(kwargs["ids_include"])
    if "ids_exclude" in kwargs.keys():
        exclude_ids = kwargs["ids_exclude"]
    if "skip_cats" in kwargs.keys():
        skip_cats=kwargs["skip_cats"]
    
    num_cats = len(data.scat2cat) - len(skip_cats)
    num_ann_scat = np.zeros(num_cats)

    exclude_ids |= img_ids
    removed = set()

    for _id in img_ids:
        result = data.load_masks(_id, return_mask=False)
        if result is None:
            continue
        _, mask_cat = result
        mask_set = set(mask_cat.tolist())
        if init_cats & mask_set or mask_set <= skip_cats:
            removed.add(_id)
        else:
            for mcat in mask_cat:
                mcat = data.cat2scat[mcat]
                if mcat not in skip_cats:
                    mcat = Dataset.scat_interval_limit(mcat, skip_cats)[0]
                    num_ann_scat[mcat] += 1
    
    img_ids -= removed
    class_freq = np.delete(class_freq, list(skip_cats))
    class_prob = inv_class_prob(class_freq)

    while num_ann_scat.std(ddof=1) < stddev and np.sum(num_ann_scat) < num_ann:
        i = np.random.choice(num_cats, p=class_prob)
        imgs_per_cats = data.imgs_per_cats(data.scat2cat[i])
        im_id = random.choice(imgs_per_cats)
        if im_id in img_ids or im_id in exclude_ids:
            continue
        result = data.load_masks(im_id, return_mask=False)
        if result is None:
            continue
        _, mask_cat = result
        mask_set = set(mask_cat.tolist())

        if mask_set <= skip_cats:
            continue

        cprob = random.random()
        if not aug_cats & mask_set or pprob > cprob:
            for mcat in mask_cat:
                mcat = data.cat2scat[mcat]
                if mcat not in skip_cats:
                    mcat = Dataset.scat_interval_limit(mcat, skip_cats)[0]
                    num_ann_scat[mcat] += 1
            img_ids.add(im_id)
    return img_ids

if __name__ == '__main__':
    ann_aug_file = "ann_aug.json"

    data = Dataset(root_path, ann_aug_file)

    scat_count = len(data.super_cat_to_cat())
    scat_hist = data.count_super_cat(scat_count)

    original_ids = np.arange(1500).tolist()
    excluded_cats = [5, 9, 19, 22, 26, 27]
    # 22 super categories remained
    excluded_cats_set = set(excluded_cats)

    train_ids = sample_data(scat_hist, 360, 20000,
                        exclude_init_cat=[4, 6],
                        pprob=0.02,
                        exclude_aug_cat=[15],
                        ids_include=original_ids,
                        skip_cats=excluded_cats_set)

test_ids = sample_data(scat_hist, 100, 2000,
                       pprob=0.055,
                       exclude_aug_cat=[15],
                       ids_exclude=train_ids,
                       skip_cats=excluded_cats_set)

    train_img_paths = data.img_paths(train_ids)
    data.create_label_file(train_img_paths, excluded_cats_set)
    data.save_data(train_img_paths, "train.txt")

    test_img_paths = data.img_paths(test_ids)
    data.create_label_file(test_img_paths, excluded_cats_set)
    data.save_data(test_img_paths, "test.txt")


