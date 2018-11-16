# Android BLE client-server prototype

Prototype implementing the server and client sides of the APDU over Bluetooth Low Energy transport protocol. The specification is published in this repository: [https://github.com/fidesmo/apdu-over-ble](https://github.com/fidesmo/apdu-over-ble)

## Usage

This application is a proof of concept for demonstrating that it is possible to transmit APDUs over BLE.  The application should be installed in two Android terminals to work
correctly.  One of these Android terminals will be the [server](#Server) (in
charge of all the NFC communication), and the other will be the [client](#Client) sending the commands to the card connected to the server.

To fully test the Client --> Server --> Secure Element connection, it is necessary to have a contactless device, like for example a [Fidesmo Card](https://developer.fidesmo.com/fidesmocard).

## Server

For activating the server mode you just need to install and start the
application on it. If phone supports BLE peripheral mode – server will be started automatically. 
Please bear in mind that not all Android phones can be BLE servers. The phone needs to have an Android 
version >= 6.0 (API level >= 23) and NFC capabilities.

For this to work correctly, please be sure to attach a contactless card to the
phone after establishing the connection. Server will print a message: `Please attach the card to the phone` which indicates that a 
card can be attached. You will know that the card is detected when the text `Card attached` appears on the screen.

<img alt="card found" src="https://github.com/fidesmo/apdu-over-ble-android/blob/master/images/card-found.png" width="50%"/>

When the phone acting as the client starts the communication, you will see all
the traces of the NFC communication in the server:

<img alt="nfc communication" src="https://github.com/fidesmo/apdu-over-ble-android/blob/master/images/NFC-traces.png" width="50%"/>

## Client

The prototype can run as Client on phones having an Android version >= 5.0 (API level >= 21).
For a phone to work as a BLE client, you need to install the app in it, and then follow the following instructions:

1. Click the "Scan" button at the bottom. This will start scanning for BLE Servers advertising:

    <img alt="scan started" src="https://github.com/fidesmo/apdu-over-ble-android/blob/master/images/scan-started.png" width="50%"/>
     
2. The client will automatically discover the peripheral device (server). If the server is this same application running in server mode, the server will ask the user to attach the NFC card to it.
If the card is a Fidesmo card (link to https://developer.fidesmo.com/fidesmocard) the card identifiers are read by the server and transmitted to the client, which displays them.

    <img alt="card information obtained" src="https://github.com/fidesmo/apdu-over-ble-android/blob/master/images/info-obtained.jpg" width="50%"/>



