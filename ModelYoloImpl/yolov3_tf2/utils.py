import sys
import numpy as np
import cv2
import tensorflow as tf


def non_max_suppresion(inputs, model_size, max_output_size,
                       max_output_size_per_class, iou_threshold, confidence_threshold):
    bbox, confs, class_probs = tf.split(inputs, [4, 1, -1], axis=-1)
    bbox = bbox / model_size[0]

    bbox = tf.reshape(bbox, (bbox.shape[1], 4))
    scores = tf.multiply(confs, class_probs)

    classes = tf.keras.backend.argmax(scores, axis=-1)[0, :]
    classes = tf.cast(classes, tf.float32)
    scores = tf.keras.backend.max(scores, axis=-1)[0, :]

    selected_indeces = tf.image.non_max_suppression(
        boxes=bbox,
        scores=scores,
        max_output_size=max_output_size,
        iou_threshold=iou_threshold,
        score_threshold=confidence_threshold)

    boxes = tf.gather(bbox, selected_indeces)  # (<10, 4)
    classes = tf.gather(classes, selected_indeces)  # (<10)
    scores = tf.gather(scores, selected_indeces)  # (<10)

    pad = [[0, max_output_size - tf.shape(boxes)[0]], [0, 0]]
    pad2 = [pad[0]]

    boxes = tf.pad(boxes, pad, "CONSTANT")
    classes = tf.pad(classes, pad2, "CONSTANT")
    scores = tf.pad(scores, pad2, "CONSTANT")

    boxes = tf.reshape(boxes, (1, tf.shape(boxes)[0], 4))
    classes = tf.reshape(classes, (1, -1))
    scores = tf.reshape(scores, (1, -1))

    valid_detections = tf.shape(selected_indeces)

    return boxes, classes, scores, valid_detections


def resize_image(inputs, model_size):
    inputs = tf.image.resize(inputs, model_size)
    return inputs


def transform_images(inputs, size):
    inputs = tf.image.resize(inputs, (size, size))
    inputs = inputs / 255
    return inputs


def load_class_names(file_name):
    '''
    Read class names from file.

    Inputs:
        file_name: string -> file path to class names
    Return:
        class_names: list -> a list with all class names
    '''
    with open(file_name, 'r') as file_stream:
        class_names = file_stream.read().splitlines()
    return class_names


def output_boxes(inputs, model_size, max_output_size, max_output_size_per_class,
                 iou_threshold, confidence_threshold):
    center_x, center_y, width, height, confidence, classes = \
        tf.split(inputs, [1, 1, 1, 1, 1, -1], axis=-1)

    top_left_x = center_x - width / 2.0
    top_left_y = center_y - height / 2.0
    bottom_right_x = center_x + width / 2.0
    bottom_right_y = center_y + height / 2.0

    inputs = tf.concat([top_left_y, top_left_x, bottom_right_y,
                        bottom_right_x, confidence, classes], axis=-1)

    boxes_dicts = non_max_suppresion(inputs, model_size, max_output_size,
                                     max_output_size_per_class, iou_threshold, confidence_threshold)
    return boxes_dicts


def draw_output(img, boxes, objectness, classes, nums, class_names):
    '''
    Draw rectangle on image from bbox.
    '''
    boxes, objectness, classes, nums = boxes[0], objectness[0], classes[0], nums[0]
    boxes = np.array(boxes)
    for i in range(int(nums)):
        y1x1 = tuple(
            (boxes[i, 1::-1] * [img.shape[0], img.shape[1]]).astype(np.int32))
        y2x2 = tuple(
            (boxes[i, 3:1:-1] * [img.shape[0], img.shape[1]]).astype(np.int32))
        print("Boxesss: ", y2x2, "  ", y1x1)
        img = cv2.rectangle(img, (y1x1), (y2x2), (255, 0, 0), 2)
        img = cv2.drawMarker(img, (y1x1), (0, 255, 0))
        img = cv2.drawMarker(img, (y2x2), (0, 255, 0))
        img = cv2.putText(img, '{} {:.4f}'.format(
            class_names[int(classes[i])], objectness[i]),
            (y1x1), cv2.FONT_HERSHEY_PLAIN, 1, (0, 0, 255), 2)
    return img
