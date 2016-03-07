#include "enet/enet.h"

#include <stdlib.h>
#include <jni.h>

#define CLIENT_TO_LONG(x) ((intptr_t)(x))
#define LONG_TO_CLIENT(x) ((ENetHost*)(intptr_t)(x))

#define PEER_TO_LONG(x) ((intptr_t)(x))
#define LONG_TO_PEER(x) ((ENetPeer*)(intptr_t)(x))

JNIEXPORT jint JNICALL
Java_com_limelight_nvstream_enet_EnetConnection_initializeEnet(JNIEnv *env, jobject class) {
    return enet_initialize();
}

JNIEXPORT jlong JNICALL
Java_com_limelight_nvstream_enet_EnetConnection_createClient(JNIEnv *env, jobject class, jstring address) {
    ENetAddress enetAddress;
    const char *addrStr;
    int err;
    
    // Perform a lookup on the address to determine the address family
    addrStr = (*env)->GetStringUTFChars(env, address, 0);
    err = enet_address_set_host(&enetAddress, addrStr);
    (*env)->ReleaseStringUTFChars(env, address, addrStr);
    if (err < 0) {
        return CLIENT_TO_LONG(NULL);
    }
    
    // Create a client that can use 1 outgoing connection and 1 channel
    return CLIENT_TO_LONG(enet_host_create(enetAddress.address.ss_family, NULL, 1, 1, 0, 0));
}

JNIEXPORT jlong JNICALL
Java_com_limelight_nvstream_enet_EnetConnection_connectToPeer(JNIEnv *env, jobject class, jlong client, jstring address, jint port, jint timeout) {
    ENetPeer* peer;
    ENetAddress enetAddress;
    ENetEvent event;
    const char *addrStr;
    int err;

    // Initialize the ENet address
    addrStr = (*env)->GetStringUTFChars(env, address, 0);    
    err = enet_address_set_host(&enetAddress, addrStr);
    enet_address_set_port(&enetAddress, port);
    (*env)->ReleaseStringUTFChars(env, address, addrStr);
    if (err < 0) {
        return PEER_TO_LONG(NULL);
    }
    
    // Start the connection
    peer = enet_host_connect(LONG_TO_CLIENT(client), &enetAddress, 1, 0);
    if (peer == NULL) {
        return PEER_TO_LONG(NULL);
    }
    
    // Wait for the connect to complete
    if (enet_host_service(LONG_TO_CLIENT(client), &event, timeout) <= 0 || event.type != ENET_EVENT_TYPE_CONNECT) {
        enet_peer_reset(peer);
        return PEER_TO_LONG(NULL);
    }
    
    // Ensure the connect verify ACK is sent immediately
    enet_host_flush(LONG_TO_CLIENT(client));

    // Set the max peer timeout to 10 seconds
    enet_peer_timeout(peer, ENET_PEER_TIMEOUT_LIMIT, ENET_PEER_TIMEOUT_MINIMUM, 10000);
    
    return PEER_TO_LONG(peer);
}

JNIEXPORT jint JNICALL
Java_com_limelight_nvstream_enet_EnetConnection_readPacket(JNIEnv *env, jobject class, jlong client, jbyteArray data, jint length, jint timeout) {
    jint err;
    jbyte* dataPtr;
    ENetEvent event;
    
    // Wait for a receive event, timeout, or disconnect
    err = enet_host_service(LONG_TO_CLIENT(client), &event, timeout);
    if (err <= 0) {
        return err;
    }
    else if (event.type != ENET_EVENT_TYPE_RECEIVE) {
        return -1;
    }
    
    // Check that the packet isn't too large
    if (event.packet->dataLength > length) {
        enet_packet_destroy(event.packet);
        return event.packet->dataLength;
    }
    
    // Copy the packet data into the caller's buffer
    dataPtr = (*env)->GetByteArrayElements(env, data, 0);
    memcpy(dataPtr, event.packet->data, event.packet->dataLength);
    err = event.packet->dataLength;
    (*env)->ReleaseByteArrayElements(env, data, dataPtr, 0);
    
    // Free the packet
    enet_packet_destroy(event.packet);
    
    return err;
}

JNIEXPORT jboolean JNICALL
Java_com_limelight_nvstream_enet_EnetConnection_writePacket(JNIEnv *env, jobject class, jlong client, jlong peer, jbyteArray data, jint length, jint packetFlags) {
    ENetPacket* packet;
    jboolean ret;
    jbyte* dataPtr;
    
    dataPtr = (*env)->GetByteArrayElements(env, data, 0);
    
    // Create the reliable packet that describes our outgoing message
    packet = enet_packet_create(dataPtr, length, packetFlags);
    if (packet != NULL) {
        // Send the message to the peer
        if (enet_peer_send(LONG_TO_PEER(peer), 0, packet) < 0) {
            // This can fail if the peer has been disconnected
            enet_packet_destroy(packet);
            ret = JNI_FALSE;
        }
        else {
            // Force the client to send the packet now
            enet_host_flush(LONG_TO_CLIENT(client));
            ret = JNI_TRUE;
        }
    }
    else {
        ret = JNI_FALSE;
    }
    
    (*env)->ReleaseByteArrayElements(env, data, dataPtr, JNI_ABORT);
    
    return ret;
}

JNIEXPORT jint JNICALL
Java_com_limelight_nvstream_enet_EnetConnection_destroyClient(JNIEnv *env, jobject class, jlong client) {
    enet_host_destroy(LONG_TO_CLIENT(client));
}

JNIEXPORT jint JNICALL
Java_com_limelight_nvstream_enet_EnetConnection_disconnectPeer(JNIEnv *env, jobject class, jlong peer) {
    enet_peer_disconnect_now(LONG_TO_PEER(peer), 0);
}
