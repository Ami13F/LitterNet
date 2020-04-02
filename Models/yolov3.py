import tensorflow as tf
from tensorflow.keras import Model
from tensorflow.keras.layers import BatchNormalization, Conv2D, Input, \
    ZeroPadding2D, LeakyReLU, UpSampling2D

import logging


class YoloV3:
    def __init__(self, cfg_file, num_classes):
        self.num_classes = num_classes
        self.blocks = self.parse_cfg(cfg_file)

    def parse_cfg(self, cfg_file):
        '''
        Parse cfg file.
        Input:
            cfg_file: string -> cfg file path

        Each key is hyperparameter and value is coresponded number, except block name, which
        have key: type, and value name of the block.

        Return: 
            blocks: list(dict) -> a list with entities key:value
        '''
        logging.info("Start reading cfg file...")
        with open(cfg_file, 'r') as file_stream:
            lines = [line.rstrip('\n') for line in file_stream.readlines()
                     if line[0] != "#" and line != '\n']

        blocks = []
        atributes = {}
        for line in lines:
            if line[0] == '[':
                line = "type=" + line[1:-1].rstrip()
                # new block
                if len(atributes) != 0:
                    blocks.append(atributes)
                    atributes = {}
            key, value = line.split("=")
            atributes[key.rstrip()] = value.lstrip()

        blocks.append(atributes)
        return blocks

    def create_network(self, model_size):
        '''
        Create Yolo network
        Input:
            model_size: (width,height,dim) -> size of model

        Transform each layer from cfg file to tensors.

        Return: 
            model -> computed tensor model 
        '''
        outputs = {}
        output_filters = []
        filters = []
        out_pred = []
        scale = 0
        # create keras input for model
        inputs = input_image = Input(shape=model_size)
        inputs = inputs / 255.0

        # Get all layers without net
        for i, block in enumerate(self.blocks[1:]):
            # If block is convolutional layer
            if block["type"] == "convolutional":
                inputs, filters, strides = self.create_convolutional(
                    block, inputs, i)

            elif block["type"] == "upsample":
                stride = int(block["stride"])
                inputs = UpSampling2D(stride)(inputs)

            # If block is route layer
            elif block["type"] == "route":
                ind_backward = list(map(int, block["layers"].split(",")))
                start = ind_backward[0]
                # two indexes for layer
                if len(ind_backward) > 1:
                    end = ind_backward[1] - i
                    filters = output_filters[i + start] + output_filters[end]
                    inputs = tf.concat(
                        [outputs[i + start], outputs[i + end]], axis=-1)
                # One index for layer
                else:
                    filters = output_filters[i + start]
                    inputs = outputs[i + start]

            # Skip layers
            elif block["type"] == "shortcut":
                backward_step = int(block["from"])
                inputs = outputs[i - 1] + outputs[i + backward_step]

            elif block["type"] == "yolo":
                inputs, initial_shape, anchors = self.create_yolo(
                    block, inputs)
                strides, prediction = self.create_prediction(
                    inputs, block, input_image, anchors, initial_shape, strides)
                if scale:
                    out_pred = tf.concat([out_pred, prediction], axis=1)
                else:
                    out_pred = prediction
                    scale = 1

            outputs[i] = inputs
            output_filters.append(filters)
        model = Model(input_image, out_pred)
        model.summary()
        return model

    def create_convolutional(self, block, inputs, i):
        '''
        Create convolutional layer.
        Input:
            block: dict -> a block with convolutional layer atributes
            inputs: tensor -> keras Input
            i: int -> current layer

        Convert sample block in keras layers.

        Return:
            inputs: tensor -> for keras
        '''
        activation = block['activation']
        pad = int(block["pad"])
        filters = int(block["filters"])
        stride = int(block["stride"])
        size = int(block["size"])
        padd = 'same'

        # Add top and left padding
        if stride > 1:
            inputs = ZeroPadding2D(((1, 0), (1, 0)))(inputs)
            padd = 'valid'

        inputs = Conv2D(filters,
                        size,
                        strides=stride,
                        padding=padd,
                        name="conv-" + str(i),
                        use_bias=False if "batch_normalize" in block else True)(inputs)

        if "batch_normalize" in block:
            inputs = BatchNormalization(name="batch_norm-" + str(i))(inputs)
            inputs = LeakyReLU(alpha=0.1, name="leaky-" + str(i))(inputs)

        return inputs, filters, stride

    def create_yolo(self, block, inputs):
        '''
        Create yolo layer.
        Input:
            block: dict -> a block with yolo layer atributes
            inputs: tensor -> keras Input            

        Convert sample block in keras layers.
        Get specific anchor boxes.

        Return:
            inputs: tensor -> for keras
            initial_shape: shape 
            anchors: list -> a list of tuples
        '''
        # Get anchors from mask indexes
        mask_ind = list(map(int, block["mask"].strip(" ").split(",")))
        anchors = list(map(int, block["anchors"].strip(" ").split(",")))
        # Make pairs
        anchors = [(anchors[i], anchors[i+1])
                   for i in range(0, len(anchors), 2)]
        anchors = [anchors[i] for i in mask_ind]
        B = len(anchors)

        # Reshape anchors
        initial_shape = inputs.get_shape().as_list()
        # [None, B * grid_size * grid_size, 5 + classes num]
        inputs = tf.reshape(
            inputs, [-1, B * initial_shape[1] * initial_shape[2], 5 + self.num_classes])

        return inputs, initial_shape, anchors

    def create_prediction(self, inputs, block, input_image, anchors, initial_shape, strides):
        '''
        Create a prediction.
        Input:
            inputs: tensor -> keras Input
            input_image: list  -> image to predict on
            block: dict -> a block with convolutional layer atributes
            anchors: list -> a list with tuples(anchor points)                        
            initial_shape: tensor shape -> initial shape of input before yolo layer
            strides: int -> stride value

        Create bounding boxes, box shapes, confidence, classes.
        Normalize parameters to [0,1]. Compute real from relative shapes for bboxs.

        Return:
            tensor -> concatenation of bcenters, bshapes, confidence, classes
        '''
        box_centers = inputs[:, :, 0:2]
        box_shapes = inputs[:, :, 2:4]
        confidence = inputs[:, :, 4:5]
        dim = len(anchors)
        classes = inputs[:, :, 5:self.num_classes + 5]
        # With sigmoid convert in 0-1 scale
        box_centers = tf.sigmoid(box_centers)
        confidence = tf.sigmoid(confidence)
        classes = tf.sigmoid(classes)

        # Multiply anchors tensor
        anchors = tf.tile(anchors, [initial_shape[1] * initial_shape[2], 1])
        box_shapes = tf.exp(box_shapes) * tf.cast(anchors, dtype=tf.float32)
        x = tf.range(initial_shape[1], dtype=tf.float32)
        y = tf.range(initial_shape[2], dtype=tf.float32)

        # Convert relative positions into real positions
        cx, cy = tf.meshgrid(x, y)
        cx = tf.reshape(cx, (-1, 1))
        cy = tf.reshape(cy, (-1, 1))
        cxy = tf.concat([cx, cy], axis=-1)
        cxy = tf.tile(cxy, [1, dim])
        cxy = tf.reshape(cxy, [1, -1, 2])

        strides = (input_image.shape[1] // initial_shape[1],
                   input_image.shape[2] // initial_shape[2])
        box_centers = (box_centers + cxy) * strides

        return strides, tf.concat([box_centers, box_shapes, confidence, classes], axis=-1)

    def show_cfg(self):
        '''
        Print all blocks from cfg file.
        '''
        for block in self.blocks:
            for key in block.keys():
                print(key + " = " + block[key])


if __name__ == '__main__':
    cfg_file = '/content/drive/My Drive/cfg/taco-yolov3-22.cfg'
    yolo = YoloV3(cfg_file, 22)
    yolo.create_network((416, 416, 3))
