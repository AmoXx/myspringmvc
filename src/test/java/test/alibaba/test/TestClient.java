package test.alibaba.test;

public class TestClient implements Runnable {
    private static Integer num = 20;

    private static ThreadLocal<Integer> numLocal = new ThreadLocal<Integer>() {
        @Override
        protected Integer initialValue() {
            return num;
        }
    };

    @Override
    public void run() {
        getNum();
    }

    private void getNum() {
        int n = numLocal.get();
        for (int i = 1; i <= 30; i++) {
            if (n > 0) {
                n--;
                System.out.println(Thread.currentThread().getName() + "剩余num: " + n);
            } else {
                break;
            }
        }
    }

    public static void main(String[] args) {
        TestClient client1 = new TestClient();
        TestClient client2 = new TestClient();
        TestClient client3 = new TestClient();

        Thread thread1 = new Thread(client1);
        Thread thread2 = new Thread(client2);
        Thread thread3 = new Thread(client3);

        thread1.start();
        thread2.start();
        thread3.start();

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println(TestClient.num);
    }
}
