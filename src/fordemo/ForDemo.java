package fordemo;

import java.io.BufferedReader;
import java.io.FileReader;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Scanner;

public class ForDemo {

    static String empFile = "C:\\Users\\User\\Documents\\MOIT01-Group64\\ForDemo\\src\\resources\\MotorPH_Employee Data - Employee Details - Employee Details.csv";
    static String attFile = "C:\\Users\\User\\Documents\\MOIT01-Group64\\ForDemo\\src\\resources\\MotorPH_Employee Data - Attendance Record - Attendance Record.csv";

    static String empNo;
    static String firstName;
    static String lastName;
    static double hourlyRate;
    static double grossSemiMonthlyRate;

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.print("Enter Employee Number: ");
        String input = scanner.nextLine().trim();
        scanner.close();

        if (!getEmployee(input)) {
            System.out.println("Employee not found.");
            return;
        }

        System.out.println("\nEmployee: " + lastName + ", " + firstName);
        System.out.printf("Hourly Rate: %.2f\n", hourlyRate);

        double totalHours = getAttendance();

        double grossWeekly = totalHours * hourlyRate;
        double grossMonthly = grossSemiMonthlyRate * 2;  // Semi-monthly × 2 = monthly gross

        double sss = grossMonthly * 0.05;
        double philhealth = grossMonthly * 0.025;
        double pagibig = grossMonthly * 0.02;

        if (pagibig > 200) {
            pagibig = 200;
        }

        double totalDeductions = sss + philhealth + pagibig;
        double netMonthlySalary = grossMonthly - totalDeductions;

        System.out.println("\n===== SALARY SUMMARY =====");
        System.out.printf("Hours Worked: %.2f\n", totalHours);
        System.out.printf("Gross Weekly Salary (based on attendance): %.2f\n", grossWeekly);
        System.out.printf("Gross Monthly Salary (from semi-monthly rate): %.2f\n", grossMonthly);

        System.out.println("\nDeductions:");
        System.out.printf("SSS: %.2f\n", sss);
        System.out.printf("PhilHealth: %.2f\n", philhealth);
        System.out.printf("Pag-IBIG: %.2f\n", pagibig);

        System.out.printf("\nNet Salary (Monthly): %.2f\n", netMonthlySalary);
    }

    static boolean getEmployee(String input) {
        try (BufferedReader br = new BufferedReader(new FileReader(empFile))) {
            br.readLine();  // Skip header line
            String line;
            while ((line = br.readLine()) != null) {
                String[] data = splitCsvLine(line);
                if (data.length <= 18) continue;

                String empNum = data[0].replace("\"", "").trim();

                if (empNum.equals(input)) {
                    lastName = data[1].replace("\"", "").trim();
                    firstName = data[2].replace("\"", "").trim();

                    grossSemiMonthlyRate = Double.parseDouble(
                        data[17].replace("\"", "").replace(",", "").trim()
                    );
                    hourlyRate = Double.parseDouble(
                        data[18].replace("\"", "").replace(",", "").trim()
                    );

                    empNo = empNum;
                    return true;
                }
            }
        } catch (Exception e) {
            System.out.println("Error reading employee file: " + e.getMessage());
        }
        return false;
    }

    static double getAttendance() {
        double totalHours = 0;
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("H:mm");

        try (BufferedReader br = new BufferedReader(new FileReader(attFile))) {
            br.readLine(); // Skip header

            String line;
            while ((line = br.readLine()) != null) {
                String[] data = splitCsvLine(line);
                if (data.length < 6) continue;

                String attEmpNo = data[0].replace("\"", "").trim();
                if (!attEmpNo.equals(empNo)) continue;

                LocalTime login = LocalTime.parse(data[4].replace("\"", "").trim(), fmt);
                LocalTime logout = LocalTime.parse(data[5].replace("\"", "").trim(), fmt);

                totalHours += computeHours(login, logout);
            }
        } catch (Exception e) {
            System.out.println("Error reading attendance file: " + e.getMessage());
        }

        return totalHours;
    }

    static double computeHours(LocalTime login, LocalTime logout) {
        LocalTime lateThreshold = LocalTime.of(8, 11);
        LocalTime cutoff = LocalTime.of(17, 0);

        if (login.isAfter(lateThreshold)) {
            login = lateThreshold;
        }

        if (logout.isAfter(cutoff)) {
            logout = cutoff;
        }

        if (!logout.isAfter(login)) {
            return 0;
        }

        long minutesWorked = Duration.between(login, logout).toMinutes();

        if (minutesWorked >= 5 * 60) {
            minutesWorked -= 60;
        }

        double hours = minutesWorked / 60.0;
        return Math.min(hours, 8.0);
    }

    static String[] splitCsvLine(String line) {
        return line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
    }
}