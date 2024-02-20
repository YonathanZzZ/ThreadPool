

import com.sun.istack.internal.NotNull;
import waitablequeue.SemWaitableQueue;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ThreadPool implements Executor {
    private final AtomicInteger currNumOfThreads;
    private int targetNumOfThreads;
    private final SemWaitableQueue<Task<?>> tasks;
    private final Semaphore pauseSem;
    private boolean isPaused;
    private final int MAX_PRIORITY;
    private final int MIN_PRIORITY;
    private boolean preventSubmit;

    public ThreadPool() {
        this(Runtime.getRuntime().availableProcessors());
    }

    public ThreadPool(int numOfThreads) {
        this.currNumOfThreads = new AtomicInteger(0);
        this.tasks = new SemWaitableQueue<>(numOfThreads);
        this.pauseSem = new Semaphore(0);
        this.MAX_PRIORITY = Priority.HIGH.getValue() + 1;
        this.MIN_PRIORITY = Priority.LOW.getValue() - 1;
        this.isPaused = false;
        this.targetNumOfThreads = numOfThreads;

        for (int i = 0; i < numOfThreads; ++i) {
            WorkingThread thread = new WorkingThread();
            thread.start();
        }
    }

    public <V> Future<V> submit(Callable<V> command, Priority priority) {
        return submit(command, priority.getValue());
    }

    public <V> Future<V> submit(Callable<V> command) {
        return submit(command, Priority.DEFAULT.getValue());
    }

    public <V> Future<V> submit(Runnable command, Priority priority) {
        Callable<V> callable = runnableToCallable(command, null);

        return submit(callable, priority.getValue());
    }

    public <V> Future<V> submit(Runnable command, Priority priority,
                                V returnValue) {
        Callable<V> callable = runnableToCallable(command, returnValue);

        return submit(callable, priority.getValue());
    }

    private <V> Future<V> submit(Callable<V> command, int priority) {
        if (preventSubmit) {
            return null;
        }

        Task<V> newTask = new Task<>(command, priority);
        tasks.enqueue(newTask);
        return newTask.getFuture();
    }

    private <V> Callable<V> runnableToCallable(Runnable runnableToWrap,
                                               V result) {
        return () -> {
            runnableToWrap.run();
            return result;
        };
    }

    @Override
    public void execute(@NotNull Runnable command) {
        submit(command, Priority.DEFAULT);
    }

    public void setNumOfThreads(int numOfThreads) {
        int diff = numOfThreads - targetNumOfThreads;

        if (diff > 0) {
            //add new threads
            if (isPaused) {
                //if ThreadPool is paused, add pause tasks so the new threads
                // won't start running tasks
                addPauseTasks(diff);
            }

            for (int i = 0; i < diff; ++i) {
                WorkingThread thread = new WorkingThread();
                thread.start();
            }

        } else if (diff < 0) {
            //create callable to destroy thread
            Callable<Void> stopThreadCommand = () -> {
                ((WorkingThread) (Thread.currentThread())).stopThread = true;
                return null;
            };

            //create task from callable
            Task<Void> stopThreadTask = new Task<>(stopThreadCommand,
                    MAX_PRIORITY);

            //add task to queue
            for (int i = 0; i < -diff; ++i) {
                tasks.enqueue(stopThreadTask);
            }
        }

        targetNumOfThreads = numOfThreads;
    }

    public void pause() {
        //create task that acquires semaphore
        //the number of instances of the task to add to the queue is equal to
        // the number of threads

        isPaused = true;

        //drain permits (set semaphore to 0), in case user called resume
        // before pause, causing the semapahore value to be increased
        // unnecessarily
        pauseSem.drainPermits();

        addPauseTasks(currNumOfThreads.get());
    }

    private void addPauseTasks(int amount) {
        Callable<Void> pauseCommand = () -> {
            pauseSem.acquire();

            return null;
        };

        Task<Void> pauseTask = new Task<>(pauseCommand, MAX_PRIORITY);

        for (int i = 0; i < amount; ++i) {
            tasks.enqueue(pauseTask);
        }
    }

    public void resume() {
        pauseSem.release(currNumOfThreads.get());
    }

    public void shutDown() {
        this.preventSubmit = true;

        Callable<Void> stopThreadCommand = () -> {
            ((WorkingThread) (Thread.currentThread())).stopThread = true;

            return null;
        };

        Task<Void> shutDownTask = new Task<>(stopThreadCommand, MIN_PRIORITY);

        for (int i = 0; i < targetNumOfThreads; ++i) {
            tasks.enqueue(shutDownTask);
        }
    }

    public void awaitTermination(long timeout, TimeUnit unit) throws TimeoutException {
        long currTime = System.currentTimeMillis();
        long maxTimeToWait =
                System.currentTimeMillis() + unit.toMillis(timeout);


        while (currNumOfThreads.get() > 0 && currTime < maxTimeToWait) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            currTime = System.currentTimeMillis();
        }

        if (currNumOfThreads.get() > 0) {
            throw new TimeoutException();
        }
    }

    public void awaitTermination() {
        while (currNumOfThreads.get() > 0) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    //WorkingThread Class

    private class WorkingThread extends Thread {

        private boolean stopThread;

        @Override
        public void run() {
            currNumOfThreads.incrementAndGet();
            while (!stopThread) {
                Task<?> task = tasks.dequeue();
                try {
                    task.execute();
                }catch (Exception e) {
                    Thread.interrupted();
                    currNumOfThreads.decrementAndGet();
                }
            }

            currNumOfThreads.decrementAndGet();
        }
    }

    //Task Class
    private class Task<V> implements Comparable<Task<V>> {

        private final int priority;
        private final TaskFuture<V> future;

        public Task(Callable<V> callable, int priority) {
            this.priority = priority;
            this.future = new TaskFuture<>(callable, this);
        }

        public void execute() {
            try {
                future.run();
            } catch (Exception e) {
                future.executionException = new ExecutionException(e);
            }
        }

        public TaskFuture<V> getFuture() {
            return this.future;
        }

        @Override
        public int compareTo(@NotNull Task<V> task) {
            return Integer.compare(task.priority, this.priority);
        }
    }

    //Priority enum
    public enum Priority {
        LOW(1),
        DEFAULT(5),
        HIGH(10);

        private final int value;

        Priority(int value) {
            this.value = value;
        }

        public int getValue() {
            return this.value;
        }
    }

    //TaskFuture Class
    private class TaskFuture<V> implements Future<V> {

        private V value;
        private final Callable<V> callable;
        private final Task<V> task;
        private Thread thread;
        private boolean isDone;
        private boolean isCancelled;
        private ExecutionException executionException;

        public TaskFuture(Callable<V> callable, Task<V> task) {
            this.callable = callable;
            this.task = task;
        }

        public void run() throws Exception {
            try {
                this.value = callable.call();
            } catch (ExecutionException e) {
                this.executionException = e;
            }

            this.thread = Thread.currentThread();
            this.isDone = true;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            //attempt to remove task from queue
            if (isDone() || isCancelled) {
                return false;
            }

            boolean removeRes = tasks.remove(task);
            if (removeRes) {
                //task was removed from queue
                return isCancelled = isDone = true;
            }

            //check if task is currently running
            if (mayInterruptIfRunning && null != thread) {
                thread.interrupt();
                return isCancelled = isDone = true;
            }

            return false;
        }

        @Override
        public boolean isCancelled() {
            return isCancelled;
        }

        @Override
        public boolean isDone() {
            return isDone;
        }

        @Override
        public V get() throws InterruptedException, ExecutionException {
            if (isCancelled()) {
                throw new CancellationException();
            }

            if (executionException != null) {
                throw executionException;
            }

            while (!isDone) {
                Thread.sleep(100);
            }

            return value;
        }

        @Override
        public V get(long timeout, @NotNull TimeUnit unit) throws
                InterruptedException, ExecutionException, TimeoutException {

            if (isCancelled()) {
                throw new CancellationException();
            }

            if (executionException != null) {
                throw executionException;
            }

            long currTime = System.currentTimeMillis();
            long timeToStopWaiting =
                    System.currentTimeMillis() + unit.toMillis(timeout);

            while (currTime < timeToStopWaiting) {
                if (isDone) {
                    return value;
                }

                Thread.sleep(100);
                currTime = System.currentTimeMillis();
            }

            throw new TimeoutException();
        }
    }
}

