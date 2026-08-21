import java.util.Scanner;

public class Main {

    public static void main(String[] args) {

        Scanner scanner = new Scanner(System.in);

        while (true) {

            System.out.println();
            System.out.println("========================================");
            System.out.println("           JOB PORTAL SYSTEM");
            System.out.println("========================================");
            System.out.println("1. User Registration");
            System.out.println("2. User Login");
            System.out.println("3. Exit");
            System.out.println("========================================");

            System.out.print("Enter your choice: ");

            int choice;

            try {
                choice = scanner.nextInt();
                scanner.nextLine();
            } catch (Exception e) {
                System.out.println("Please enter a valid number.");
                scanner.nextLine();
                continue;
            }

            switch (choice) {

                case 1:
                    UserRegistration.registerUser();
                    break;

                case 2:
                    UserLogin.loginUser();
                    break;

                case 3:
                    System.out.println();
                    System.out.println("========================================");
                    System.out.println("Thank you for using Job Portal System!");
                    System.out.println("========================================");
                    scanner.close();
                    return;

                default:
                    System.out.println();
                    System.out.println("Invalid choice. Please try again.");
            }
        }
    }
}