package com.aspirecsl.labs;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.awaitility.Duration;
import org.junit.After;
import org.junit.Test;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Unit test for the <tt>ThreadCache</tt> class
 *
 * @author anoopr
 */
public class ThreadCacheTest {

    private final ExecutorService executor = Executors.newFixedThreadPool(5);

    @After
    public void teardown() throws InterruptedException {
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.MINUTES);
    }

    @Test
    public void addsOneAccountToTheCorrectThreadTag() throws InterruptedException, ExecutionException, TimeoutException {
        final List<String> componentNumbers = new ArrayList<>();

        final List<Callable<String>> tasks = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            final int[] arr = { i };
            tasks.add(() -> {
                ThreadCache.initialise();
                final Component component = new Component("" + arr[0]);
                ThreadCache.add("default", component);
                final String componentLabel = ThreadCache.get(Component.class, "default").label;
                ThreadCache.close();
                return componentLabel;
            });
        }

        final List<Future<String>> results = executor.invokeAll(tasks);

        for (Future<String> result : results) {
            componentNumbers.add(result.get(10, SECONDS));
        }
        assertThat(componentNumbers)
                .as("Account Numbers From Context")
                .containsExactlyInAnyOrder("1", "2", "3", "4", "5");
    }

    @Test
    public void addsMultipleAccountsToTheCorrectThreadTag() throws InterruptedException, ExecutionException, TimeoutException {
        final List<String> componentNumbers = new ArrayList<>();

        final List<Callable<String>> tasks = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            final int[] arr = { i };
            tasks.add(() -> {
                ThreadCache.initialise();
                final Component componentOne = new Component("1" + arr[0]);
                final Component componentTwo = new Component("2" + arr[0]);
                ThreadCache.add("first", componentOne);
                ThreadCache.add("second", componentTwo);
                final String componentLabel = ThreadCache.get(Component.class, "second").label;
                ThreadCache.close();
                return componentLabel;
            });
        }

        final List<Future<String>> results = executor.invokeAll(tasks);

        for (Future<String> result : results) {
            componentNumbers.add(result.get(10, SECONDS));
        }
        assertThat(componentNumbers)
                .as("Account Numbers From Context")
                .containsExactlyInAnyOrder("21", "22", "23", "24", "25");
    }

    @Test
    public void componentIsGarbageCollectibleOnceTestCompletes() throws InterruptedException {

        ThreadCache.initialise();
        final Component componentOne = new Component("1");
        ThreadCache.add("first", componentOne);
        final Component componentTwo = new Component("2");
        ThreadCache.add("second", componentTwo);
        assertThat(ThreadCache.isEmpty())
                .as("Account Numbers Should Not Be Emptied Before Context close")
                .isFalse();

        ThreadCache.close();
        System.gc();

        //@formatter:off
        // waits a maximum of 10 sec for a GC run; checking the result evey 100 milliseconds
        await()
            .atMost(Duration.TEN_SECONDS)
        .with()
            .pollInterval(Duration.ONE_HUNDRED_MILLISECONDS)
            .until(ThreadCache::isEmpty);
        //@formatter:on
    }

    /** Simple object to test the cache **/
    private static class Component {
        private final String label;

        private Component(String label) {
            this.label = label;
        }
    }
}