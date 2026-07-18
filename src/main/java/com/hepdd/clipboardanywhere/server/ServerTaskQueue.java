package com.hepdd.clipboardanywhere.server;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public final class ServerTaskQueue {

    public static final ServerTaskQueue INSTANCE = new ServerTaskQueue();
    private final Queue<Runnable> tasks = new ConcurrentLinkedQueue<>();

    private ServerTaskQueue() {}

    public void enqueue(Runnable task) {
        if (task != null) tasks.offer(task);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Runnable task;
        while ((task = tasks.poll()) != null) {
            task.run();
        }
    }
}
