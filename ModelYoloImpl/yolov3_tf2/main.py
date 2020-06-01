import tensorflow as tf
from utils import load_class_names, output_boxes, draw_output, resize_image, transform_images
import cv2
import numpy as np
from yolov3 import YoloV3
import os.path

physical_devices = tf.config.experimental.list_physical_devices('GPU')
assert len(physical_devices) > 0, "Not enough GPU hardware devices available"
tf.config.experimental.set_memory_growth(physical_devices[0], True)


def detect_image(detect, class_names_file, model_size, model):
    print("Detecting objects loading....")
    if detect == False:
        return

    image_file = "bottle2.jpeg"
    img_path = os.path.abspath("yolov3_tf2\\" + image_file)
    class_names = load_class_names(class_names_file)

    img_raw = tf.image.decode_image(
        open(img_path, 'rb').read(), channels=3)

    # create one more dimension for batch (1, 416, 416, 3)
    img = tf.expand_dims(img_raw, 0)
    print(img.shape)
    img = transform_images(img, model_size[0])  # 416 size
    # boxes, classes, scores, nums = model.predict(resized_frame)
    boxes, classes, scores, nums = model(img)
    for i in range(nums[0]):
        print('\t{}, {}, {}'.format(class_names[int(classes[0][i])],
                                    np.array(scores[0][i]),
                                    np.array(boxes[0][i])))

    # image = np.squeeze(image)
    img = cv2.cvtColor(img_raw.numpy(), cv2.COLOR_RGB2BGR)
    img = cv2.resize(img, dsize=(model_size[0], model_size[0]))  # 416 size
    img = draw_output(img, boxes, scores,
                      classes, nums, class_names)    
    win_name = 'Detected objects'
    cv2.imshow(win_name, img)
    cv2.waitKey(0)
    cv2.destroyAllWindows()
    
    # For saving the result
    cv2.imwrite(os.path.abspath('yolov3_tf2\\img\\test-3.jpg'), img)
    print("Image saved....")


def convert(model, model_name):
    print("Converting model to tflite.....")

    # image_file = "people.jpg"
    # img_path = os.path.abspath("yolov3_tf2\\" + image_file)
    # class_names = load_class_names(class_names_file)
    # image = cv2.imread(img_path)
    # image = np.array(image)
    # image = tf.expand_dims(image, 0)
    # resized_frame = resize_image(image, (model_size[0], model_size[1]))

    # model.predict(resized_frame)

    converter = tf.lite.TFLiteConverter.from_saved_model("saved")
    # converter = tf.lite.TFLiteConverter.from_keras_model(model)

    converter.target_spec.supported_ops = [
        tf.lite.OpsSet.TFLITE_BUILTINS,
        tf.lite.OpsSet.SELECT_TF_OPS
    ]
    # For old convertor TOCO
    converter.experimental_new_converter = False

    # converter.experimental_new_converter = True

    # # Convert the model
    # # post_training_quantize = True
    tflite_model = converter.convert()

    open(model_name, "wb").write(tflite_model)
    print("Tflite model saved")


if __name__ == '__main__':
    print("start...")
    # class_names_file = os.path.abspath("yolov3_tf2\cfg\\taco-10.names")
    # cfg_file = os.path.abspath("yolov3_tf2\cfg\yolov3-10b-angle.cfg")
    # weights_file = os.path.abspath(
    #     "yolov3_tf2\weights\yolov3-taco.tf")
    label_names = "taco-10.names"
    cfg_name = "yolov3-10b-angle.cfg"
    weights_filedest = "yolov3.tf"
    class_names_file = os.path.abspath("yolov3_tf2\cfg\\" + label_names)
    cfg_file = os.path.abspath("yolov3_tf2\cfg\\" + cfg_name)

    weights_file = os.path.abspath(
        "yolov3_tf2\weights\\" + weights_filedest)

    model_size = (416, 416, 3)
    num_classes = 10
    max_output_size = 10
    max_output_size_per_class = 5
    iou_threshold = 0.5
    confidence_threshold = 0.3

    # Change parameters value for different run modes
    detect = False
    conversion = True

    yolo = YoloV3(cfg_file, model_size, num_classes,
                  iou_threshold, confidence_threshold)
    model = yolo.create_network()
    model.load_weights(weights_file)  # .expect_partial()

    # model.build(model.input.shape)

    detect_image(detect, class_names_file, model_size, model)

    if conversion:
        tflite_model_name = "yolov3.tflite"
        # save the model as .pb
        print("Start saving model as pb....")
        tf.saved_model.save(model, 'saved')
        # model.save('model.h5')
        # # Create a converter and tflite model
        print("End model conversion as pb....")
        convert(model, tflite_model_name)
    
