import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Scanner;

public class UserRegistration {

    @SuppressWarnings("resource")
    public static void registerUser() {
        Scanner scanner = new Scanner(System.in);

        System.out.println("\n========================================");
        System.out.println("          USER REGISTRATION");
        System.out.println("========================================");

        System.out.print("Enter Full Name: ");
        String fullName = scanner.nextLine();

        System.out.print("Enter Email: ");
        String email = scanner.nextLine();

        System.out.print("Enter Password: ");
        String password = scanner.nextLine();

        System.out.print("Enter Phone Number: ");
        String phone = scanner.nextLine();

        System.out.println("\nSelect User Type:\n1. Job Seeker\n2. Recruiter\n3. Admin");
        System.out.print("Enter choice: ");
        int choice = scanner.nextInt();
        scanner.nextLine();

        String userType;
        switch (choice) {
            case 1 -> userType = "JOB_SEEKER";
            case 2 -> {
                userType = "RECRUITER";
                System.out.print("Enter Recruiter Security Password: ");
                String recruiterPass = scanner.nextLine();
                if (!"recruiter".equalsIgnoreCase(recruiterPass) && !"admin".equalsIgnoreCase(recruiterPass)) {
                    System.out.println("\nRegistration failed! Invalid Recruiter Security Password.");
                    return;
                }
            }
            case 3 -> {
                userType = "ADMIN";
                System.out.print("Enter Admin Security Password: ");
                String adminPass = scanner.nextLine();
                if (!"admin".equalsIgnoreCase(adminPass) && !"admin123".equalsIgnoreCase(adminPass)) {
                    System.out.println("\nRegistration failed! Invalid Admin Security Password.");
                    return;
                }
            }
            default -> {
                System.out.println("Invalid user type!");
                return;
            }
        }

        String sql = "INSERT INTO users (full_name, email, password, phone, user_type) VALUES (?, ?, ?, ?, ?)";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, fullName);
            statement.setString(2, email);
            statement.setString(3, password);
            statement.setString(4, phone);
            statement.setString(5, userType);

            if (statement.executeUpdate() > 0) {
                System.out.println("\n========================================");
                System.out.println(" Registration successful!");
                System.out.println("========================================");
                System.out.println("Name      : " + fullName);
                System.out.println("Email     : " + email);
                System.out.println("User Type : " + userType);
            }
        } catch (SQLException e) {
            System.out.println("\nRegistration failed! Error: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        registerUser();
    }
}