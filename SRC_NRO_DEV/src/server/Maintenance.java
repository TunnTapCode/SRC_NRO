package server;
import java.util.concurrent.Executors;
import services.Service;
import utils.Logger;

public class Maintenance extends Thread {

    public static boolean isRunning = false;
    public static int maintenanceDelayMinutes = 1;
// bảo trì game false
    private static Maintenance i;

    private int time;

    public static int getMaintenanceDelayMinutes() {
        return maintenanceDelayMinutes > 0 ? maintenanceDelayMinutes : 1;
    }

    public static void setMaintenanceDelayMinutes(int minutes) {
        if (minutes > 0) {
            maintenanceDelayMinutes = minutes;
        }
    }

    private Maintenance() {

    }

    public static Maintenance gI() {
        if (i == null) {
            i = new Maintenance();
        }
        return i;
    }

    public void start(int min) {
        if (!isRunning) {
            isRunning = true;
            this.time = 15;
            this.start();
        }
    }

    /**
     * Bắt đầu đếm ngược bảo trì với thời gian cho trước (giây).
     * Khi isRunning bị set về false giữa chừng (admin hủy),
     * vòng lặp sẽ thoát mà không gọi ServerManager.close().
     */
    public void startNew(int seconds) {
        if (!isRunning) {
            isRunning = true;
            this.time = Math.max(1, seconds);
            int minutes = this.time / 60;
            int secs = this.time % 60;
            String timeText;
            if (minutes > 0 && secs == 0) {
                timeText = minutes + " phút";
            } else if (minutes > 0) {
                timeText = minutes + " phút " + secs + " giây";
            } else {
                timeText = this.time + " giây";
            }
            Service.gI().sendThongBaoAllPlayer("Hệ thống sẽ bảo trì sau " + timeText + " nữa. Vui lòng thoát game ngay để tránh mất mát vật phẩm!");
            Executors.newSingleThreadExecutor().submit(this, "Thread Bảo Trì");
        }
    }

    public void startImmediately() {
        if (!isRunning) {
            isRunning = true;
            Logger.log(Logger.YELLOW, "BEGIN MAINTENANCE\n");
            ServerManager.gI().close();
        }
    }

    @Override
    public void run() {
        while (this.time > 0) {
            // Nếu admin hủy bảo trì giữa chừng thì dừng
            if (!isRunning) {
                Logger.log(Logger.YELLOW, "BẢO TRÌ ĐÃ BỊ HỦY BỞI ADMIN\n");
                Service.gI().sendThongBaoAllPlayer("Admin đã hủy bảo trì. Server tiếp tục hoạt động bình thường!");
                i = null; // reset singleton để có thể gọi startNew lần sau
                return;
            }

            if (this.time == 60) {
                Service.gI().sendThongBaoAllPlayer("Hệ thống sẽ bảo trì sau 1 phút nữa hãy thoát game ngay để tránh mất mát vật phẩm.");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                }
                this.time--;
            } else if (time < 60) {
                Service.gI().sendThongBaoAllPlayer("Hệ thống sẽ bảo trì sau " + time + " giây nữa");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                }
                this.time--;
            } else {
                int hour = this.time / 3600;
                int min = (this.time - hour * 3600) / 60;
                int sec = this.time % 60;

                String hourStr = (hour > 0) ? hour + " giờ " : "";
                String minStr = (min > 0) ? min + " phút " : "";
                String secStr = (sec > 0) ? sec + " giây " : "";

                Service.gI().sendThongBaoAllPlayer("Hệ thống sẽ bảo trì sau " + hourStr + minStr + secStr
                        + "nữa");
                Logger.log(Logger.YELLOW, "Hệ thống sẽ bảo trì sau " + hourStr + minStr + secStr
                        + "nữa\n");
                if (sec == 0 && this.time > 60) {
                    sec = 60;
                } else if (sec == 0) {
                    sec = 1;
                }
                this.time -= sec;
                try {
                    Thread.sleep(sec * 1000);
                } catch (InterruptedException e) {
                }
            }
        }
        Logger.log(Logger.YELLOW, "BEGIN MAINTENANCE\n");
        i = null; // reset singleton để lần sau startNew được
        ServerManager.gI().close();
    }

}
