# EMV Applet for Javacard 2.2.1

This is a fully working EMV applet for javacard 2.2.1.

Current STANDARD features are:
- Data access and basic instructions (SELECT, PIN VERIFY, PROCESSING OPTIONS, GENERATE AC)

Current CUSTOM features are:
- Received APDU logging (for debugging purposes);
- PIN VERIFY always return OK (9000);
- Fixed IAD and AC output (can be "hot swapped" without having to reflash the cap file, using PUT DATA commands);

Unfortunately, no personalization commands are implemented. You will need to edit the source code to change the EF data. You can also use my other tool, [ArrayEdit](https://github.com/tiosolid/array_edit), to make this task a lot easier.

# Notice

This applet was stitched using source code from all over the internet and a lot of my own code. The `Crypto.java` file was entirely made by another person, but I don't remember where I found it, sorry :(

# Building

This applet must be built using a very ancient version of Eclipse (INDIGO SR2 3.7.2) and JCOP Tools from IBM (Google is your friend for this one) since I didn't have access to newer jcop cards while testing this.

# Disclaimer

Use this applet at your own risk. Im not responsible for anything you do with it.