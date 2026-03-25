package fordemo;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class ForDemo {
    
    // Use relative paths - files should be in src/resources/
    private static final String EMP_FILE = "src/resources/MotorPH_Employee Data - Employee Details - Employee Details.csv";
    private static final String ATT_FILE = "src/resources/MotorPH_Employee Data - Attendance Record - Attendance Record.csv";
    
    // Grace period: 8:00-8:10 (exactly 8:10 as per requirement)
    private static final LocalTime GRACE_PERIOD_END = LocalTime.of(8, 10);
    private static final LocalTime CUT_OFF_TIME = LocalTime.of(17, 0);
    private static final int LUNCH_BREAK_MINUTES = 60;
    private static final int MAX_DAILY_HOURS = 8;
    
    // Date formatters for CSV parsing
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("M/d/yyyy");
    private static final DateTimeFormatter TIME_FORMAT_SECONDS = DateTimeFormatter.ofPattern("H:mm:ss");
    private static final DateTimeFormatter TIME_FORMAT_MINUTES = DateTimeFormatter.ofPattern("H:mm");

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        
        // Step 1: Login System
        User currentUser = login(scanner);
        if (currentUser == null) {
            System.out.println("Login failed. Exiting...");
            scanner.close();
            return;
        }
        
        // Step 2: Role-based menu
        showRoleBasedMenu(scanner, currentUser);
        
        scanner.close();
    }
    
    /**
     * Handles user login with username and password
     */
    private static User login(Scanner scanner) {
        System.out.println("=== PAYROLL SYSTEM LOGIN ===");
        int loginAttempts = 3;
        
        while (loginAttempts > 0) {
            System.out.print("Enter Username: ");
            String username = scanner.nextLine().trim();
            System.out.print("Enter Password: ");
            String password = scanner.nextLine().trim();
            
            // Updated credentials as per requirement
            if ("employee".equals(username) && "12345".equals(password)) {
                return new User("employee", "Employee", UserRole.EMPLOYEE);
            } else if ("payroll_staff".equals(username) && "12345".equals(password)) {
                return new User("payroll_staff", "Payroll Staff", UserRole.PAYROLL);
            }
            
            loginAttempts--;
            if (loginAttempts > 0) {
                System.out.println("Invalid credentials. " + loginAttempts + " attempts remaining.");
            }
        }
        
        return null;
    }
    
    /**
     * Displays menu based on user role
     */
    private static void showRoleBasedMenu(Scanner scanner, User user) {
        while (true) {
            System.out.println("\n=== PAYROLL SYSTEM ===");
            System.out.println("Logged in as: " + user.name + " (" + user.username + ")");
            
            if (user.role == UserRole.EMPLOYEE) {
                System.out.println("1. View My Payroll");
            } else if (user.role == UserRole.PAYROLL) {
                System.out.println("1. Single Employee Payroll");
                System.out.println("2. All Employees Payroll");
            }
            
            System.out.println("0. Logout");
            System.out.print("Choose option: ");
            
            String choice = scanner.nextLine().trim();
            
            switch (choice) {
                case "1":
                    if (user.role == UserRole.EMPLOYEE) {
                        processMyPayroll(scanner, user);
                    } else {
                        processSingleEmployeePayroll(scanner);
                    }
                    break;
                case "2":
                    if (user.role == UserRole.PAYROLL) {
                        processAllEmployeesPayroll();
                    } else {
                        System.out.println("Access denied for your role.");
                    }
                    break;
                case "0":
                    System.out.println("Logging out...");
                    return;
                default:
                    System.out.println("Invalid option.");
            }
        }
    }
    
    /**
     * Process payroll for logged-in employee (Employee role only)
     */
    private static void processMyPayroll(Scanner scanner, User user) {
        // For employee role, automatically use their own employee number
        // In a real system, this would come from their profile
        System.out.print("Enter your Employee Number: ");
        String empNo = scanner.nextLine().trim();
        
        Employee employee = loadEmployee(empNo);
        if (employee == null) {
            System.out.println("Employee not found or you don't have access.");
            return;
        }
        
        CutOffHours cutOffHours = loadAttendanceByCutOff(empNo);
        PayrollSummary summary = calculatePayroll(employee, cutOffHours);
        
        printPayrollSummary(employee, cutOffHours, summary);
    }
    
    /**
     * Process payroll for a single employee (Payroll Staff only)
     */
    private static void processSingleEmployeePayroll(Scanner scanner) {
        System.out.print("Enter Employee Number: ");
        String empNo = scanner.nextLine().trim();
        
        Employee employee = loadEmployee(empNo);
        if (employee == null) {
            System.out.println("Employee not found.");
            return;
        }
        
        CutOffHours cutOffHours = loadAttendanceByCutOff(empNo);
        PayrollSummary summary = calculatePayroll(employee, cutOffHours);
        
        printPayrollSummary(employee, cutOffHours, summary);
    }
    
    /**
     * Process payroll for all employees (Payroll Staff only)
     */
    private static void processAllEmployeesPayroll() {
        List<Employee> employees = loadAllEmployees();
        System.out.println("\n=== ALL EMPLOYEES PAYROLL SUMMARY ===");
        
        double totalCompanyPayroll = 0;
        for (Employee emp : employees) {
            CutOffHours hours = loadAttendanceByCutOff(emp.empNo);
            PayrollSummary summary = calculatePayroll(emp, hours);
            System.out.printf("%s, %s (%s): Total Net Pay = %.2f\n", 
                            emp.lastName, emp.firstName, emp.empNo, summary.totalNetPay);
            totalCompanyPayroll += summary.totalNetPay;
        }
        System.out.printf("TOTAL COMPANY PAYROLL: %.2f\n", totalCompanyPayroll);
    }
    
    /**
     * Load single employee by employee number
     */
    private static Employee loadEmployee(String empNo) {
        try (BufferedReader br = new BufferedReader(new FileReader(EMP_FILE))) {
            br.readLine(); // Skip header
            String line;
            while ((line = br.readLine()) != null) {
                String[] data = splitCsvLine(line);
                if (data.length <= 18) continue;
                
                String fileEmpNo = data[0].replace("\"", "").trim();
                if (fileEmpNo.equals(empNo)) {
                    return parseEmployeeData(data);
                }
            }
        } catch (IOException e) {
            System.out.println("Error reading employee file: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Load all employees from CSV
     */
    private static List<Employee> loadAllEmployees() {
        List<Employee> employees = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(EMP_FILE))) {
            br.readLine(); // Skip header
            String line;
            while ((line = br.readLine()) != null) {
                String[] data = splitCsvLine(line);
                if (data.length > 18) {
                    employees.add(parseEmployeeData(data));
                }
            }
        } catch (IOException e) {
            System.out.println("Error reading employee file: " + e.getMessage());
        }
        return employees;
    }
    
    /**
     * Parse employee data from CSV row
     */
    private static Employee parseEmployeeData(String[] data) {
        Employee emp = new Employee();
        emp.empNo = data[0].replace("\"", "").trim();
        emp.lastName = data[1].replace("\"", "").trim();
        emp.firstName = data[2].replace("\"", "").trim();
        emp.grossSemiMonthlyRate = Double.parseDouble(
            data[17].replace("\"", "").replace(",", "").trim());
        emp.hourlyRate = Double.parseDouble(
            data[18].replace("\"", "").replace(",", "").trim());
        return emp;
    }
    
    /**
     * Load attendance records and separate by cut-off periods (1-15, 16-end)
     */
    private static CutOffHours loadAttendanceByCutOff(String empNo) {
        CutOffHours hours = new CutOffHours();
        
        try (BufferedReader br = new BufferedReader(new FileReader(ATT_FILE))) {
            br.readLine(); // Skip header
            String line;
            while ((line = br.readLine()) != null) {
                String[] data = splitCsvLine(line);
                if (data.length < 6) continue;
                
                String attEmpNo = data[0].replace("\"", "").trim();
                if (!attEmpNo.equals(empNo)) continue;
                
                LocalDate attDate = parseDate(data[3].replace("\"", "").trim());
                if (attDate == null) continue;
                
                LocalTime login = parseTimeFlexible(data[4].replace("\"", "").trim());
                LocalTime logout = parseTimeFlexible(data[5].replace("\"", "").trim());
                if (login == null || logout == null) continue;
                
                double dailyHours = computeDailyHours(login, logout);
                if (attDate.getDayOfMonth() <= 15) {
                    hours.firstCutOffHours += dailyHours;
                } else {
                    hours.secondCutOffHours += dailyHours;
                }
            }
        } catch (IOException e) {
            System.out.println("Error reading attendance file: " + e.getMessage());
        }
        
        return hours;
    }
    
    /**
     * Parse date with error handling
     */
    private static LocalDate parseDate(String dateStr) {
        try {
            return LocalDate.parse(dateStr, DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
    
    /**
     * Parse time flexibly (H:mm:ss or H:mm)
     */
    private static LocalTime parseTimeFlexible(String timeStr) {
        try {
            return LocalTime.parse(timeStr, TIME_FORMAT_SECONDS);
        } catch (DateTimeParseException e) {
            try {
                return LocalTime.parse(timeStr, TIME_FORMAT_MINUTES);
            } catch (DateTimeParseException ex) {
                return null;
            }
        }
    }
    
    /**
     * Calculate daily hours with grace period (8:00-8:10), lunch deduction, and max 8 hours
     */
    private static double computeDailyHours(LocalTime login, LocalTime logout) {
        // Apply grace period: if login <= 8:10, treat as 8:00
        if (login.isBefore(GRACE_PERIOD_END) || login.equals(GRACE_PERIOD_END)) {
            login = LocalTime.of(8, 0);
        }
        
        // Cut-off at 5:00 PM
        if (logout.isAfter(CUT_OFF_TIME)) {
            logout = CUT_OFF_TIME;
        }
        
        if (!logout.isAfter(login)) {
            return 0;
        }
        
        long minutesWorked = Duration.between(login, logout).toMinutes();
        
        // Deduct 1 hour lunch if 5+ hours worked
        if (minutesWorked >= 300) {
            minutesWorked -= LUNCH_BREAK_MINUTES;
        }
        
        double hours = minutesWorked / 60.0;
        return Math.min(hours, MAX_DAILY_HOURS);
    }
    
    /**
     * Calculate complete payroll summary
     */
    private static PayrollSummary calculatePayroll(Employee emp, CutOffHours hours) {
        PayrollSummary summary = new PayrollSummary();
        
        // First cut-off: hours * hourly rate (no deductions)
        summary.grossFirstCutOff = hours.firstCutOffHours * emp.hourlyRate;
        summary.netFirstCutOff = summary.grossFirstCutOff;
        
        // Second cut-off: fixed semi-monthly rate with government deductions
        summary.grossSecondCutOff = emp.grossSemiMonthlyRate;
        
        // Government deductions applied ONLY on second cut-off
        summary.sss = emp.grossSemiMonthlyRate * 0.05;
        summary.philhealth = emp.grossSemiMonthlyRate * 0.025;
        summary.pagibig = Math.min(emp.grossSemiMonthlyRate * 0.02, 200);
        summary.tax = calculateTax(emp.grossSemiMonthlyRate);
        
        summary.totalDeductions = summary.sss + summary.philhealth + 
                                 summary.pagibig + summary.tax;
        summary.netSecondCutOff = summary.grossSecondCutOff - summary.totalDeductions;
        summary.totalNetPay = summary.netFirstCutOff + summary.netSecondCutOff;
        
        return summary;
    }
    
    /**
     * Print formatted payroll summary
     */
    private static void printPayrollSummary(Employee emp, CutOffHours hours, PayrollSummary summary) {
        System.out.println("\n===== EMPLOYEE PAYROLL SUMMARY =====");
        System.out.printf("Employee: %s, %s (%s)\n", emp.lastName, emp.firstName, emp.empNo);
        System.out.printf("Hourly Rate: %.2f\n\n", emp.hourlyRate);
        
        System.out.println("FIRST CUT-OFF (1-15):");
        System.out.printf("Hours: %.2f\n", hours.firstCutOffHours);
        System.out.printf("Gross Pay: %.2f\n", summary.grossFirstCutOff);
        System.out.printf("Net Pay (No deductions): %.2f\n\n", summary.netFirstCutOff);
        
        System.out.println("SECOND CUT-OFF (16-End):");
        System.out.printf("Gross Semi-Monthly: %.2f\n\n", summary.grossSecondCutOff);
        
        System.out.println("GOVERNMENT DEDUCTIONS:");
        System.out.printf("SSS: %.2f\n", summary.sss);
        System.out.printf("PhilHealth: %.2f\n", summary.philhealth);
        System.out.printf("Pag-IBIG: %.2f\n", summary.pagibig);
        System.out.printf("Tax: %.2f\n", summary.tax);
        System.out.printf("Total Deductions: %.2f\n\n", summary.totalDeductions);
        
        System.out.printf("Net Pay (2nd Cut-off): %.2f\n", summary.netSecondCutOff);
        System.out.printf("TOTAL MONTHLY NET PAY: %.2f\n", summary.totalNetPay);
    }
    
    /**
     * Calculate tax based on semi-monthly gross pay
     */
    private static double calculateTax(double semiMonthlyGross) {
        double monthlyGross = semiMonthlyGross * 2;
        
        if (monthlyGross <= 20833) return 0;
        else if (monthlyGross <= 33333) return (monthlyGross - 20833) * 0.20 / 2;
        else if (monthlyGross <= 66667) return (2500 + (monthlyGross - 33333) * 0.25) / 2;
        else if (monthlyGross <= 166667) return (10833 + (monthlyGross - 66667) * 0.30) / 2;
        else if (monthlyGross <= 666667) return (40833 + (monthlyGross - 166667) * 0.32) / 2;
        else return (200833 + (monthlyGross - 666667) * 0.35) / 2;
    }
    
    /**
     * CSV splitter that handles commas within quotes
     */
    private static String[] splitCsvLine(String line) {
        return line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
    }
    
    // Data classes - no global variables
    static class Employee {
        String empNo;
        String firstName;
        String lastName;
        double hourlyRate;
        double grossSemiMonthlyRate;
    }
    
    static class CutOffHours {
        double firstCutOffHours = 0.0;
        double secondCutOffHours = 0.0;
    }
    
    static class PayrollSummary {
        double grossFirstCutOff;
        double netFirstCutOff;
        double grossSecondCutOff;
        double sss, philhealth, pagibig, tax;
        double totalDeductions;
        double netSecondCutOff;
        double totalNetPay;
    }
    
    static class User {
        String username;
        String name;
        UserRole role;
        
        User(String username, String name, UserRole role) {
            this.username = username;
            this.name = name;
            this.role = role;
        }
    }
    
    enum UserRole {
        EMPLOYEE, PAYROLL
    }
}