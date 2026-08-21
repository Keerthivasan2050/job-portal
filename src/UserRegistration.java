import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Scanner;

public class UserRegistration {

    public static void registerUser() {

        try (Scanner scanner = new Scanner(System.in)) {

            System.out.println();
            System.out.println("========================================");
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

            System.out.println();
            System.out.println("Select User Type:");
            System.out.println("1. Job Seeker");
            System.out.println("2. Recruiter");
            System.out.println("3. Admin");

            System.out.print("Enter choice: ");
            int choice = scanner.nextInt();

            String userType;

            switch (choice) {
                case 1:
                    userType = "JOB_SEEKER";
                    break;

                case 2:
                    userType = "RECRUITER";
                    System.out.print("Enter Recruiter Security Password: ");
                    scanner.nextLine(); // consume newline if needed
                    String recruiterPass = scanner.nextLine();
                    if (!"recruiter".equalsIgnoreCase(recruiterPass) && !"admin".equalsIgnoreCase(recruiterPass)) {
                        System.out.println();
                        System.out.println("Registration failed!");
                        System.out.println("Invalid Recruiter Security Password.");
                        return;
                    }
                    break;

                case 3:
                    userType = "ADMIN";
                    System.out.print("Enter Admin Security Password: ");
                    scanner.nextLine(); // consume newline if needed
                    String adminPass = scanner.nextLine();
                    if (!"admin".equalsIgnoreCase(adminPass) && !"admin123".equalsIgnoreCase(adminPass)) {
                        System.out.println();
                        System.out.println("Registration failed!");
                        System.out.println("Invalid Admin Security Password.");
                        return;
                    }
                    break;

                default:
                    System.out.println("Invalid user type!");
                    return;
            }

            String sql = """
                    INSERT INTO users
                    (full_name, email, password, phone, user_type)
                    VALUES (?, ?, ?, ?, ?)
                    """;

            try (Connection connection = DatabaseConnection.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {

                statement.setString(1, fullName);
                statement.setString(2, email);
                statement.setString(3, password);
                statement.setString(4, phone);
                statement.setString(5, userType);

                int rowsInserted = statement.executeUpdate();

                if (rowsInserted > 0) {
                    System.out.println();
                    System.out.println("========================================");
                    System.out.println(" Registration successful!");
                    System.out.println("========================================");
                    System.out.println("Name      : " + fullName);
                    System.out.println("Email     : " + email);
                    System.out.println("User Type : " + userType);
                }

            } catch (SQLException e) {

                System.out.println();
                System.out.println("Registration failed!");
                System.out.println("Error: " + e.getMessage());
            }
        }
    }

    public static void main(String[] args) {
        registerUser();
    }
}