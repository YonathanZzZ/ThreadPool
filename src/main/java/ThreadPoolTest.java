
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class ThreadPoolTest {
    private ThreadPool threadPool;

    @BeforeEach
    void init() {
        threadPool = new ThreadPool(4);
    }

    @AfterEach
    void end() {
        threadPool.shutDown();
    }

    @Test
    void submitCallablePriority() throws Exception {
        Callable<String> task = () -> "Test";
        Future<String> future = threadPool.submit(task,
                ThreadPool.Priority.HIGH);
        assertEquals("Test", future.get());
    }

    @Test
    void submitRunnablePriorityWithReturnValue() throws Exception {
        Runnable task = () -> System.out.println("Running task");
        Future<String> future = threadPool.submit(task, ThreadPool.Priority.HIGH, "Done");
        assertEquals("Done", future.get());
    }

    @Test
    public void pauseAndResume() {
        Runnable task = () -> {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            System.out.println("Running task");
        };
        for (int i =0 ; i < 10;++i){
            threadPool.submit(task, ThreadPool.Priority.DEFAULT);
        }
        Future<Void> future = threadPool.submit(task, ThreadPool.Priority.LOW);

        threadPool.pause();
        System.out.println("threads paused");
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        assertFalse(future.isDone());
        System.out.println("threads resumed");
        threadPool.resume();
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void shutDown() throws ExecutionException, InterruptedException {
        Callable<String> longRunningTask = () -> {
            Thread.sleep(3000);
            return "Finished";
        };
        Callable<String> task = () -> "Test";

        Future<String > future = threadPool.submit(longRunningTask);
        threadPool.shutDown();
        threadPool.awaitTermination();
        assertNull(threadPool.submit(task));
        assertEquals("Finished", future.get());
        assertTrue(future.isDone());
    }

    @Test
    void setNumOfThreads() throws Exception {
        Callable<String> task1 = () -> {
            threadPool.setNumOfThreads(2);
            return "Changed";
        };

        Callable<String> task2 = () -> "Task";

        threadPool.submit(task1);
        Future<String> future = threadPool.submit(task2);
        assertEquals("Task", future.get());
    }


    @Test
    void awaitTerminationWithTimeout() throws TimeoutException {
        Callable<Void> longRunningTask = () -> {
            Thread.sleep(3000);
            return null;
        };
        Future<Void> future = threadPool.submit(longRunningTask);
        threadPool.shutDown();
        threadPool.awaitTermination(5,TimeUnit.SECONDS);

        assertTrue(future.isDone());
    }

    @Test
    void cancel() {
        Callable<String> longRunningTask = () -> {
            Thread.sleep(10000);
            return "Finished";
        };
        for (int i = 0; i < 4 ; ++i) {
            threadPool.submit(longRunningTask, ThreadPool.Priority.HIGH);
        }
        Future<String> future = threadPool.submit(longRunningTask);
        assertTrue(future.cancel(true));
        assertTrue(future.isCancelled());
        assertThrows(CancellationException.class, () -> future.get(1, TimeUnit.SECONDS));
    }
}