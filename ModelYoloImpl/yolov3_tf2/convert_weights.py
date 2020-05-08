from yolov3 import YoloV3
import numpy as np
import os.path


class WeightsConvertor():
    def __init__(self, model, weights_file):
        self.__block = model.blocks
        self.model = model.create_network()
        self.weights_file = weights_file

    def load_weights(self):
        with open(self.weights_file, 'rb') as fp:
            # skip header info
            np.fromfile(fp, dtype=np.int32, count=5)

            blocks = self.__block

            for i, block in enumerate(blocks[1:]):
                if block["type"] == "convolutional":
                    conv_layer = self.model.get_layer("conv-" + str(i))

                    filters = conv_layer.filters
                    k_size = conv_layer.kernel_size[0]
                    input_dim = conv_layer.input_shape[-1]

                    if "batch_normalize" in block:
                        norm_layer = self.model.get_layer(
                            "batch_norm-" + str(i))
                        print("layer:", i+1, norm_layer)
                        # get 4 params
                        bn_weights = np.fromfile(
                            fp, dtype=np.float32, count=4 * filters)
                        # read beta, gamma, mean, variance
                        bn_weights = bn_weights.reshape(
                            (4, filters))[[1, 0, 2, 3]]
                    else:
                        conv_bias = np.fromfile(
                            fp, dtype=np.float32, count=filters)

                    # darknet shape (out_dim, in_dim, height, width)
                    conv_shape = (filters, input_dim, k_size, k_size)
                    conv_weights = np.fromfile(
                        fp, dtype=np.float32, count=np.product(conv_shape))
                    print(conv_weights.shape)
                    conv_weights = conv_weights.reshape(
                        conv_shape).transpose([2, 3, 1, 0])

                    if "batch_normalize" in block:
                        norm_layer.set_weights(bn_weights)
                        conv_layer.set_weights([conv_weights])
                    else:
                        conv_layer.set_weights([conv_weights, conv_bias])


if __name__ == '__main__':
    # cfg_name = "yolov3-10b-angle.cfg"
    # weights_filename = "yolov3-taco.weights"
    # weights_filedest = "yolov3-taco.tf"

    # cfg_name = "yolov3-coco.cfg"
    # weights_filename = "yolov3-coco.weights"
    # weights_filedest = "yolov3-coco.tf"

    # cfg_name = "tiny.cfg"
    # weights_filename = "yolov3-tiny.weights"
    # weights_filedest = "yolov3-tiny.tf"

    cfg_name = "yolov3-10b-angle.cfg"
    # weights_filename = "yolov3-tiny-prn.weights"
    weights_filename = "yolov3-taco.weights"
    weights_filedest = "yolov3.tf"

    cfg_file = os.path.abspath('yolov3_tf2\cfg\\' + cfg_name)
    weights_file = os.path.abspath('yolov3_tf2\weights\\' + weights_filename)
    weights_dest = os.path.abspath('yolov3_tf2\weights\\' + weights_filedest)

    num_classes = 10
    model_size = (416, 416, 3)
    iou_threshold = 0.5
    confidence_threshold = 0.1

    yolo = YoloV3(cfg_file, model_size, num_classes,
                  iou_threshold, confidence_threshold)
    # yolo.show_cfg()
    print("Blocks size: ", len(yolo.blocks))
    wc = WeightsConvertor(yolo, weights_file)
    model = wc.model
    try:
        wc.load_weights()
        model.save_weights(weights_dest)
        print("Saved file: " + weights_dest.split("/")[-1])

    except IOError as e:
        print(e)
