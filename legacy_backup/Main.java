import java.util.Scanner;

/**
 * Main Class - The entry point for the Job Portal System CLI console menu.
 * Beginners can run this class to either launch the Web Server or test the CLI registration/login.
 */
public class Main {

    public static void main(String[] args) {

        Scanner scanner = new Scanner(System.in);

        while (true) {

            System.out.println();
            System.out.println("========================================");
            System.out.println("          JOB PORTAL SYSTEM");
            System.out.println("========================================");
            System.out.println("1. Start Web Server & Open Website (http://localhost:8080)");
            System.out.println("2. User Registration (Console)");
            System.out.println("3. User Login (Console)");
            System.out.println("4. Exit");
            System.out.println("========================================");

            System.out.print("Enter your choice (1-4): ");

            int choice;

            try {
                choice = scanner.nextInt();
                scanner.nextLine();
            } catch (Exception e) {
                System.out.println("Please enter a valid number (1-4).");
                scanner.nextLine();
                continue;
            }

            switch (choice) {

                case 1:
                    System.out.println();
                    System.out.println("Starting WebServer on http://localhost:8080...");
                    try {
                        WebServer.main(new String[0]);
                    } catch (Exception e) {
                        System.out.println("Error starting server: " + e.getMessage());
                    }
                    return;

                case 2:
                    UserRegistration.registerUser();
                    break;

                case 3:
                    UserLogin.loginUser();
                    break;

                case 4:
                    System.out.println();
                    System.out.println("========================================");
                    System.out.println("Thank you for using Job Portal System!");
                    System.out.println("========================================");
                    scanner.close();
                    return;

                default:
                    System.out.println();
                    System.out.println("Invalid choice. Please enter a number between 1 and 4.");
            }
        }
    }
}