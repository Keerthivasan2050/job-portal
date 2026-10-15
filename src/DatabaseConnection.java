import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConnection {

    private static final String URL =
            "jdbc:mysql://localhost:3306/jobportal"
            + "?useSSL=false"
            + "&allowPublicKeyRetrieval=true"
            + "&serverTimezone=UTC";

    private static final String USER = "root";

    // PUT YOUR CURRENT MYSQL ROOT PASSWORD HERE
    private static final String PASSWORD = "JobPortal@123";

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    public static void main(String[] args) {

        System.out.println("======================================");
        System.out.println("          JOB PORTAL SYSTEM");
        System.out.println("======================================");

        try (Connection connection = getConnection()) {

            System.out.println("MySQL connection successful: " + (connection != null ? connection.getCatalog() : "active"));
            System.out.println("Database: jobportal");
            System.out.println("Server: localhost");
            System.out.println("Port: 3306");
            System.out.println("Connection closed successfully.");

        } catch (SQLException e) {

            System.out.println("MySQL connection failed!");
            System.out.println("Error: " + e.getMessage());
        }
    }
}