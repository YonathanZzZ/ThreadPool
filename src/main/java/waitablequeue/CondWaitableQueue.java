package waitablequeue;

import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class CondWaitableQueue<E> {
    private final Condition notFull;
    private final Condition notEmpty;
    private final ReentrantLock lock;
    private final int capacity;
    private final PriorityQueue<E> queue;

    public CondWaitableQueue(int capacity) {
        this(null, capacity);
    }

    public CondWaitableQueue(Comparator<E> comparator, int capacity) {
        this.capacity = capacity;
        this.queue = new PriorityQueue<>(capacity, comparator);
        this.lock = new ReentrantLock();
        this.notEmpty = this.lock.newCondition();
        this.notFull = this.lock.newCondition();
    }

    public boolean enqueue(E element) {
        boolean res = false;

        lock.lock();

        try {
            while (queue.size() == capacity) {
                notFull.await();
            }

            res = queue.add(element);
            notEmpty.signal();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            lock.unlock();
        }

        return res;
    }


    public E dequeue() {
        E removedItem = null;

        lock.lock();

        try {
            while (queue.isEmpty()) {
                notEmpty.await();
            }

            removedItem = queue.poll();
            notFull.signal();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            lock.unlock();
        }

        return removedItem;
    }


    public boolean remove(E element) {
        boolean res = false;

        lock.lock();

        try {
            res = queue.remove(element);
            if (res) {
                notFull.signal();
            }
        } finally {
            //unlock in finally to ensure that the lock is unlocked even if
            // the thread is interrupted after acquiring the lock
            lock.unlock();
        }

        return res;
    }

    public int size() {
        int size = 0;
        lock.lock();
        try {
            size = queue.size();
        } finally {
            lock.unlock();
        }

        return size;
    }

    public boolean isEmpty() {
        boolean res = false;

        lock.lock();

        try {
            res = queue.isEmpty();

        } finally {
            lock.unlock();
        }

        return res;
    }

    public E peek() {
        E readItem = null;

        lock.lock();

        try {
            while (queue.isEmpty()) {
                notEmpty.await();
            }

            readItem = queue.peek();

        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            notEmpty.signal();
            lock.unlock();
        }

        return readItem;
    }
}