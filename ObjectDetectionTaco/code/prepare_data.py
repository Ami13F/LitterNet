import csv
import os
import tools
import dataset_utils

import numpy as np

from sklearn.model_selection import train_test_split

def load_dataset(root_path, dataset_dir, classes):
    class_map = {}
    with open(os.path.join(root_path, classes)) as csvfile:
        reader = csv.reader(csvfile)
        class_map = {row[0]:row[1] for row in reader}

    round = None
    subset = "train"
    dataset = dataset_utils.Taco()
    taco = dataset.load_taco(dataset_dir, round, subset, class_map=class_map, return_taco=True)
    dataset.prepare()
    return dataset, taco

ROOT_PATH = os.path.abspath("..")
datasets = list()

with open(os.path.abspath("datasets.csv")) as csvfile:
    for line in csv.reader(csvfile):
        ds = dict()
        ds["data_dir"], ds["classes"], ds["train_file"], ds["valid_file"] = line
        datasets.append(ds)

for ds in datasets:
    data_dir = os.path.join(ROOT_PATH, ds["data_dir"])
    classes = os.path.abspath(ds["classes"])
    train_file = ds["train_file"]
    valid_file = ds["valid_file"]
    print(data_dir, classes, train_file, valid_file)
    dataset, taco = load_dataset(ROOT_PATH, data_dir, classes)

    img_ids = [i['id'] for i in dataset.image_info]
    X_train, X_valid = train_test_split(np.array(img_ids), test_size=0.2)

    file_to_id = dict()
    id_to_file = dict()

    for img_id in img_ids:
        filename = taco.imgs[img_id]['file_name']

        file_to_id[filename] = img_id
        id_to_file[img_id] = filename

    image_paths = tools.get_dataset_files(data_dir)
    tools.create_label_file(taco, data_dir, file_to_id, image_paths)

    train_files = [id_to_file[i] for i in X_train]
    valid_files = [id_to_file[i] for i in X_valid]

    tools.save_data(ROOT_PATH, train_files, train_file, data_dir)
    tools.save_data(ROOT_PATH, valid_files, valid_file, data_dir)

# print("class Count: {}".format(dataset.num_classes))
# for i, info in enumerate(dataset.class_info):
#     print("{:3}. {:50}".format(i, info['name']))

# ok = input("Do you wish to continue[y/n]?:")
# if ok != 'y':
#     exit()