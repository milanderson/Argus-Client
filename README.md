# IR-Server
A client server model for processing image recognition requests

A skeleton model of the client code is available in the python module "irclient.py"

The server can be run from "irserver.py." This module can be configured to run as daemon on a .nix client - change the .run() command to .start(). The server accepts input from the client, formats it into a jpg if necessary, then passes it to an image recognition network, and returns the response to the client.

The most recent Android client build is available at ArgusClientv1.0-release.apk. This has been compiled using Android Studio. The base code is in the project folder IRClient_Android, and the java basecode is located at /app/src/main/java/sbu/irclient. To point the client at a new server address, edit the "SERVER_IP" and "SERVER_PORT" variables in the IRClient_NetTask class.

The Android client can be configured to accept three types of server responses: plain text classifications, bounding box coordinates, and a RLE encoded bitmask. These correspond to the 'update_guesses,' 'update_rects,' and 'update_bitmask' calls in the IRClient_NetTask thread.
