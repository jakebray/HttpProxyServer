HttpProxyServer
===============

This is an HTTP Proxy Server that handles only the GET command. All resources are fetched from the server and passed to the client. If any of the resources are jpeg images, then any faces detected will be blurred before sending the images to the client.

There is currently an instance of this server running on the Amazon cloud that can be accessed using the following:
-------------------------------------------------------------------------------------------------------------------

address: 54.186.194.129
port: 20000
