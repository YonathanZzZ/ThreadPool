# ThreadPool

This is a custom thread pool implementation in Java designed to simplify multi-threading for multiple tasks without creating a new thread for each task.

## Introduction

A thread pool is a design pattern that enables the efficient management and utilization of a group of threads. It is particularly useful in scenarios where tasks need to be executed concurrently, and creating a new thread for each task is inefficient or impractical.

## Features

- Allows users to submit tasks to be executed by the thread pool.
- A task can be either a Runnable or a Callable, with optional Priority
- Users can specify the number of threads to be created in the pool or use the default, which creates as many threads as there are CPU cores.
- Provides a `Future` object upon task submission for tracking task status and obtaining results.
- Implements a waitable priority queue to manage task execution order.
- Enables dynamic adjustment of the thread pool size while it's running.
- Offers methods for pausing and resuming thread pool operations.
- Supports non-blocking as well as blocking termination of the thread pool.

## Methods

### ThreadPool

- `submit(task: Runnable/Callable, priority: enum)`: Add a new task to be executed by the thread pool.
- `setNumOfThreads(numThreads: int)`: Change the number of threads in the pool.
- `pause()`: Pause all threads until `resume()` is called (does not pause tasks in execution).
- `resume()`: Resume the operation of the thread pool.
- `shutDown()`: Close the thread pool (non-blocking).
- `awaitTermination(timeout: long, unit: TimeUnit)`: Block the calling thread until all threads in the pool are closed (with an optional timeout).

### Future (Returned Object)

- `cancel(boolean mayInterruptIfRunning)`: Remove a task from the queue or interrupt the running thread if applicable.
- `isCanceled()`: Check if a task was canceled.
- `isDone()`: Check if a task has completed.
- `get()`: Get the result value of a callable. This method is blocking. Throws an exception if the task was canceled or encountered an exception.
- `get(timeout: long, unit: TimeUnit)`: Get the result value of a callable with a specified timeout.

## Usage

```java
// create a thread pool with 8 threads
ThreadPool tp = new ThreadPool(8);

// submit a callable that returns a String, with high priority
Future<String> callableFuture = tp.submit(someCallable, Priority.HIGH);


// submit a Runnable that returns an Integer, with default priority
Future<Integer> runnableFuture = tp.submit(someRunnable);

// block the thread until the Callable task is done and get its value
String returnVal = callableFuture.get();

// pause the thread pool
tp.pause(); 

// resume the thread pool
tp.resume();

// shutdown the thread pool (non blockcing)
tp.shutDown();

// wait for all threads to close...
tp.awaitTermination();

// ... or with a timeout of 5 seconds
tp.awaitTermination(5, TimeUnit.SECONDS);
```
