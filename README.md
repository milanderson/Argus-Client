# IR-Server
A client server model for processing image recognition requests

A skeleton model of the client code is available in the python module "irclient.py"

The server can be run from "irserver.py." This module can be configured to run as daemon on a *nix client - simply change the .run() command to .start()

The most recent android client build is available at app-debug.apk. This has been compiled using Android Studio. The base code is in the project folder IRClient_Android, and the java basecode is located at /app/src/main/java/sbu/irclient. To point the client at a new server address, edit the "SERVER_IP" and "SERVER_PORT" variables in the IRClient_NetTask class.
