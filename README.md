# Android BLE client

Library implementing the client side of the APDU over Bluetooth Low Energy
transport protocol.

## Usage

This application is a proof of concept for demonstrating that APDUs over BLE are
possible.  The application should be installed in two Android terminals to work
correctly.  One of these Android terminals will be the [server](#Server) (in
charge of all the NFC communication), and the other will be the [client](#Client).

## Server

For activating the server mode in the terminal you just need to install the
application in it, and run it.  Please remember that not all the Android phones
can be a BLE server.  Phones that can behave as BLE Server include, at the time
of writing: (Nexus 5X, Nexus 6P, Nexus 6, Nexus 9, Moto E 4G LTE, LG G4, Galaxy S6)

For this to work correctly, please be sure to attach a Fidesmo card to the
phone after establishing the connection. Server will print a message: `Please attach the card to the phone` which indicates that 
card can be attached. You will know that the card is detected when the text `Card found` appears in the screen.

![card found](https://github.com/fidesmo/android-ble-server/blob/master/images/card-found.jpg)

When the phone acting as the client starts the communication, you will see all
the traces of the NFC communication in the server:

![nfc communication](https://github.com/fidesmo/android-ble-server/blob/master/images/NFC-traces.jpg)

## Client

For a phone to work as a BLE client, you need to install the app in it, and then
follow the following instructions:

1. Click the scan button at the bottom.  This will start scanning for BLE
   Servers advertising.  ![scan started](https://github.com/fidesmo/android-ble-server/blob/master/images/scan-started.jpg)
2. Click install button.  This action does all the actual work.  Sends the APDUs
   over BLE and receives the response from the card.
