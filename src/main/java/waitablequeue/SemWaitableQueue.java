package waitablequeue;

import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.concurrent.Semaphore;

public class SemWaitableQueue<E> {
    private final Semaphore itemsInQueueSemaphore;
    private final PriorityQueue<E> queue;
    private final Object queueLock;

    public SemWaitableQueue(int capacity) {
        this(null, capacity);
    }

    public SemWaitableQueue(Comparator<E> comparator, int capacity) {
        this.queue = new PriorityQueue<>(capacity, comparator);
        this.itemsInQueueSemaphore = new Semaphore(0);
        this.queueLock = new Object();
    }

    public boolean enqueue(E element) {
        boolean res = false;
        synchronized (queueLock) {
            res = queue.add(element);
        }
        if (res) {
            itemsInQueueSemaphore.release();
        }

        return res;
    }

    public E dequeue() {
        //block if no item is available to dequeue
        try {
            itemsInQueueSemaphore.acquire();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        synchronized (queueLock) {
            return queue.poll();
        }
    }

    public boolean remove(E element) {
        boolean removeResult = false;
        synchronized (queueLock) {
            removeResult = queue.remove(element);
        }
        if (removeResult) {
            //if item was removed, decrement semaphore
            try {
                itemsInQueueSemaphore.acquire();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        return removeResult;
    }

    public int size() {
        synchronized (queueLock) {
            return queue.size();
        }
    }

    public E peek() {
        E readItem = null;

        try {
            itemsInQueueSemaphore.acquire();
            synchronized (queueLock) {
                readItem = queue.peek();
            }

        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            itemsInQueueSemaphore.release();
        }

        return queue.peek();
    }

    public boolean isEmpty() {
        synchronized (queueLock) {
            return queue.isEmpty();
        }
    }
}