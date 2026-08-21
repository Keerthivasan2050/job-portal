import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Scanner;

public class UserLogin {

    public static void loginUser() {

        try (Scanner scanner = new Scanner(System.in)) {

            System.out.println();
            System.out.println("========================================");
            System.out.println("             USER LOGIN");
            System.out.println("========================================");

            System.out.print("Enter Email: ");
            String email = scanner.nextLine();

            System.out.print("Enter Password: ");
            String password = scanner.nextLine();

            String sql = """
                    SELECT user_id, full_name, user_type
                    FROM users
                    WHERE email = ? AND password = ?
                    """;

            try (Connection connection = DatabaseConnection.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {

                statement.setString(1, email);
                statement.setString(2, password);

                ResultSet resultSet = statement.executeQuery();

                if (resultSet.next()) {

                    System.out.println();
                    System.out.println("========================================");
                    System.out.println("          LOGIN SUCCESSFUL");
                    System.out.println("========================================");
                    System.out.println("Welcome, " + resultSet.getString("full_name") + "!");
                    System.out.println("User ID   : " + resultSet.getInt("user_id"));
                    System.out.println("User Type : " + resultSet.getString("user_type"));
                    System.out.println("========================================");

                } else {

                    System.out.println();
                    System.out.println("Login failed!");
                    System.out.println("Invalid email or password.");
                }

            } catch (SQLException e) {

                System.out.println();
                System.out.println("Login failed!");
                System.out.println("Database error: " + e.getMessage());
            }
        }
    }

    public static void main(String[] args) {
        loginUser();
    }
}