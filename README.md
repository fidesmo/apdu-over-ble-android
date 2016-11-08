# Android BLE client-server prototype

Prototype implementing the server and client sides of the APDU over Bluetooth Low Energy transport protocol. The specification is published in this repository: [https://github.com/fidesmo/apdu-over-ble](https://github.com/fidesmo/apdu-over-ble)

## Usage

This application is a proof of concept for demonstrating that it is possible to transmit APDUs over BLE.  The application should be installed in two Android terminals to work
correctly.  One of these Android terminals will be the [server](#Server) (in
charge of all the NFC communication), and the other will be the [client](#Client) sending the commands to the card connected to the server.

To fully test the Client --> Server --> Secure Element connection, it is necessary to have a contactless device, like for example a [Fidesmo Card](https://developer.fidesmo.com/fidesmocard).

## Server

For activating the server mode in the terminal you just need to install the
application in it, and run it.  Please bear in mind that not any Android phone
can be a BLE server. The phone needs to have an Android version >= 6.0 (API level >= 23) and NFC capabilities.

For this to work correctly, please be sure to attach a contactless card to the
phone after establishing the connection. Server will print a message: `Please attach the card to the phone` which indicates that a 
card can be attached. You will know that the card is detected when the text `Card found` appears in the screen.

![card found](https://github.com/fidesmo/android-ble-server/blob/master/images/card-found.jpg)

When the phone acting as the client starts the communication, you will see all
the traces of the NFC communication in the server:

![nfc communication](https://github.com/fidesmo/android-ble-server/blob/master/images/NFC-traces.jpg)

## Client

The prototype can run as Client on phones having an Android version >= 5.0 (API level >= 21).
For a phone to work as a BLE client, you need to install the app in it, and then follow the following instructions:

1. Click the scan button at the bottom.  This will start scanning for BLE
   Servers advertising:

     ![scan started](https://github.com/fidesmo/android-ble-server/blob/master/images/scan-started.jpg)
2. Click the install button.  This action does all the actual work: it sends the APDUs over BLE and receives the response from the card.

