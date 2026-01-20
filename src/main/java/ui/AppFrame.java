package ui;

import model.User;
import javax.swing.*;
import java.awt.*;

public class AppFrame extends JFrame {
    
    private final CardLayout cardLayout;
    private final JPanel cardPanel;
    private LoginPanel loginPanel;
    private MainPanel mainPanel;
    
    public AppFrame() {
        super("PortfolioTrackingSystem");
        
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        
        // CardLayout ile panel değiştirme
        cardLayout = new CardLayout();
        cardPanel = new JPanel(cardLayout);
        setContentPane(cardPanel);
        
        // Login panelini oluştur ve ekle
        loginPanel = new LoginPanel(this);
        cardPanel.add(loginPanel, "LOGIN");
        
        // İlk olarak login ekranını göster
        showLogin();
    }
    
    /**
     * Login ekranını gösterir
     */
    public void showLogin() {
        cardLayout.show(cardPanel, "LOGIN");
    }
    
    /**
     * Ana ekranı gösterir (login başarılı olduktan sonra)
     * @param user Giriş yapan kullanıcı
     */
    public void showMain(User user) {
        // MainPanel henüz oluşturulmadıysa oluştur
        if (mainPanel == null) {
            mainPanel = new MainPanel(user);
            cardPanel.add(mainPanel, "MAIN");
        } else {
            // Eğer zaten varsa, kullanıcıyı güncelle (logout/login senaryosu için)
            mainPanel.setUser(user);
        }
        
        // Ana ekrana geç
        cardLayout.show(cardPanel, "MAIN");
        
        // Pencereyi yeniden boyutlandır (tam ekran için)
        revalidate();
        repaint();
    }
}
