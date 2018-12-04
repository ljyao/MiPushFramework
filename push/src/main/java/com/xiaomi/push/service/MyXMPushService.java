package com.xiaomi.push.service;

public abstract class MyXMPushService extends XMPushService{
    @Override
    public ClientEventDispatcher createClientEventDispatcher() {
        return new MyClientEventDispatcher();
    }

}
