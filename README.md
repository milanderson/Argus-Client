# Argus Client
An Android image recognition client for leveraging remote, large scale neural networks.

httpServ_example.py: an example receiving server that will reply with dummy classes, bounding boxes and bitmasks. Responses are an echo of the received four character image ID, follwed by a JSON string with four keys: class, confidence, bounding_box, and bit_mask. Because the client sends data in YUV-12 format, the server should reformat the image to the preferred format of the nueral network. The client can also be configured to send images in JPG format from the ClassRequester class.

Boxify.py: sample code for generating bounding box predictions.
