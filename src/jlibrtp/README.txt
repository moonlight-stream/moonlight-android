jlibrtp - Java RTP Library

Kepp, Arne
ak2618@columbia.edu

Columbia University
New York, NY 10027
USA

This library was started as a term project in VoIP Security, a class taught by 
Prof. Henning Schulzrinne at Columbia University. Version 0.1 (not released as such)
was written by Vaishnav Janardhan (vj2135@columbia.edu) and Arne Kepp (ak2618@columbia.edu).

This version was rewritten by Arne Kepp, as a student project under the supervision of 
Prof. Henning Schulzrinne, Columbia University.

------Abstract
jlibrtp is a library that implements the Real-Time Transport Protocol (RTP), 
a well-established standard for streaming media across IP-based networks, in Java. 
The purpose  of this library is to make it easy for application developers to 
create applications for peer to peer streaming of audio, video and other data. 
In addition, developers will need a protocol to establish contact with peers, 
such as Session Initialization Protocol (SIP) and/or SDP.

The library accepts any kind of binary data, handles packet parsing and reordering, 
maintains a participant database and the control connection associated with the 
protocol. The application is notified of received data through a callback-interface. 
The library supports IPv4, IPv6 and multicast. It does currently not support encryption, 
and should not be used in cases where confidentiality is important before this has 
been remedied.

Please refer to http://jlibrtp.org for more information and newer versions.

The library requires Sun Microsystems Java 1.5.0 or greater, or equivalent.

The Library is licensed under the GNU Lesser General Public License, see LICENSE.txt

The demonstration programs can be compiled as follows:
javac ./jlibrtpDemos/SoundSenderDemo.java jlibrtp/*.java