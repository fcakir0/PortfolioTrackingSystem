import com.formdev.flatlaf.FlatLightLaf;
import ui.AppFrame;

import javax.swing.*;

public class App {
    public static void main(String[] args) {
        FlatLightLaf.setup();
        SwingUtilities.invokeLater(() -> new AppFrame().setVisible(true));
    }
}
