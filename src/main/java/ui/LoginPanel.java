package ui;

import dao.UserDao;
import model.User;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class LoginPanel extends JPanel {

    private final JTextField txtUsername = new JTextField(20);
    private final JPasswordField txtPassword = new JPasswordField(20);
    private final JButton btnLogin = new JButton("Giriş Yap");
    private final JLabel lblStatus = new JLabel(" ");

    private final UserDao userDao = new UserDao();
    private final AppFrame appFrame;
    private StarfieldPanel starfieldPanel;
    private JPanel cardPanel;

    public LoginPanel(AppFrame appFrame) {
        this.appFrame = appFrame;
        
        setLayout(new BorderLayout());
        
        // Açık tema renkleri
        Color bg = new Color(248, 249, 250); // Açık gri arka plan
        Color card = Color.WHITE; // Beyaz kart
        Color border = new Color(220, 224, 230); // Açık gri border
        Color text = new Color(33, 37, 41); // Koyu metin
        Color muted = new Color(108, 117, 125); // Gri metin
        Color accent = new Color(25, 135, 84); // Koyu yeşil

        // Starfield arka plan paneli
        starfieldPanel = new StarfieldPanel();
        starfieldPanel.setOpaque(true); // Koyu arka planın görünmesi için opak yap

        // JLayeredPane kullanarak starfield'i arka plana, cardPanel'i ön plana koyacağız
        JLayeredPane layeredPane = new JLayeredPane();
        layeredPane.setLayout(null); // Null layout kullan
        layeredPane.setBackground(bg);
        layeredPane.setBorder(new EmptyBorder(18, 18, 18, 18));
        add(layeredPane, BorderLayout.CENTER);

        // Şeffaf ve yuvarlatılmış panel - arkasındaki yıldızlar görünsün
        cardPanel = new RoundedPanel(20, new Color(255, 255, 255, (int)(255 * 0.18))); // %18 opaklık
        cardPanel.setLayout(new GridBagLayout());
        cardPanel.setOpaque(false); // Şeffaf arka plan için
        cardPanel.setBorder(new EmptyBorder(24, 28, 24, 28));

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(8, 8, 8, 8);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0; c.gridy = 0; c.gridwidth = 2;

        JLabel title = new JLabel("Portfolio Tracking System");
        title.setFont(new Font("Segoe UI", Font.BOLD, 18));
        title.setForeground(text);
        cardPanel.add(title, c);

        c.gridy++;
        JLabel subtitle = new JLabel("Lütfen giriş yapın");
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        subtitle.setForeground(muted);
        cardPanel.add(subtitle, c);

        // Username
        c.gridy++; c.gridwidth = 1;
        cardPanel.add(styledLabel("Kullanıcı Adı:", muted), c);
        c.gridx = 1;
        styleField(txtUsername, card, text, border);
        cardPanel.add(txtUsername, c);

        // Password
        c.gridx = 0; c.gridy++;
        cardPanel.add(styledLabel("Şifre:", muted), c);
        c.gridx = 1;
        
        //  göz ikonu butonu
        final char defaultEcho = txtPassword.getEchoChar();
        JPanel passwordWrapper = createPasswordWithToggle(txtPassword, card, text, border, muted, defaultEcho);
        cardPanel.add(passwordWrapper, c);


        c.gridx = 0; c.gridy++; c.gridwidth = 2;
        lblStatus.setForeground(new Color(178, 34, 34)); // Koyu kırmızı
        lblStatus.setHorizontalAlignment(SwingConstants.CENTER);
        cardPanel.add(lblStatus, c);

        // Butonnn
        c.gridy++; c.gridwidth = 2;
        styleButton(btnLogin, accent);
        cardPanel.add(btnLogin, c);

        // CardPanel'in doğal boyutunu hesapla
        cardPanel.setOpaque(true);
        cardPanel.validate();
        Dimension cardSize = cardPanel.getPreferredSize();
        
        // Panel boyutlarını al (henüz görünür değilse varsayılan değerleri kullan)
        int panelWidth = getWidth() > 0 ? getWidth() : 1920; // Tam ekran için varsayılan genişlik
        int panelHeight = getHeight() > 0 ? getHeight() : 1080; // Tam ekran için varsayılan yükseklik
        
        // Starfield paneli arka plana ekle (tüm panel'i kaplasın)
        starfieldPanel.setBounds(0, 0, panelWidth, panelHeight);
        layeredPane.add(starfieldPanel);
        layeredPane.setLayer(starfieldPanel, JLayeredPane.DEFAULT_LAYER);

        // CardPanel'i ön plana ekle (merkeze konumlandır)
        int cardX = (panelWidth - cardSize.width) / 2;
        int cardY = (panelHeight - cardSize.height) / 2;
        cardPanel.setBounds(cardX, cardY, cardSize.width, cardSize.height);
        layeredPane.add(cardPanel);
        layeredPane.setLayer(cardPanel, JLayeredPane.PALETTE_LAYER); // Ön plana

        // Panel boyutu değiştiğinde starfield ve cardPanel'i güncelle
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                int panelWidth = getWidth();
                int panelHeight = getHeight();
                if (panelWidth > 0 && panelHeight > 0) {
                    starfieldPanel.setBounds(0, 0, panelWidth, panelHeight);
                    Dimension cardSize = cardPanel.getPreferredSize();
                    int cardX = (panelWidth - cardSize.width) / 2;
                    int cardY = (panelHeight - cardSize.height) / 2;
                    cardPanel.setBounds(cardX, cardY, cardSize.width, cardSize.height);
                }
            }
        });

        // Enter'a basınca da giriş yapsması için
        // JPanel için rootPane yok, bu yüzden ActionListener kullanıyoruz
        txtPassword.addActionListener(e -> doLogin());
        txtUsername.addActionListener(e -> doLogin());

        btnLogin.addActionListener(e -> doLogin());
        
        // Starfield animasyonunu başlat
        starfieldPanel.startAnimation();
    }
    
    @Override
    public void removeNotify() {
        // Panel kaldırıldığında animasyonu durdur
        if (starfieldPanel != null) {
            starfieldPanel.stopAnimation();
        }
        super.removeNotify();
    }
    
    private void doLogin() {
        String username = txtUsername.getText().trim();
        String password = new String(txtPassword.getPassword());

        if (username.isEmpty() || password.isEmpty()) {
            lblStatus.setText("Kullanıcı adı ve şifre boş olamaz.");
            return;
        }
        User user = userDao.findByUsernameAndPassword(username, password);

        if (user != null) {
            // Başarılı login - AppFrame'e ana ekrana geçmesini söyle
            appFrame.showMain(user);
        } else {
            lblStatus.setText("Kullanıcı adı veya şifre hatalı!");
            txtPassword.setText("");
        }
    }
    
    private JLabel styledLabel(String text, Color color) {
        JLabel lbl = new JLabel(text);
        lbl.setForeground(color);
        lbl.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        return lbl;
    }
    
    private void styleField(JTextField field, Color bg, Color fg, Color border) {
        field.setBackground(bg);
        field.setForeground(fg);
        field.setCaretColor(fg);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(border, 1, true),
                new EmptyBorder(8, 10, 8, 10) // FlatLaf için daha fazla padding
        ));
    }
    
    private void styleButton(JButton button, Color bg) {
        button.setBackground(bg);
        button.setForeground(Color.WHITE);
        button.setFont(new Font("Segoe UI", Font.BOLD, 14));
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
    }
    
    private JPanel createPasswordWithToggle(JPasswordField field, Color bg, Color fg, Color border, Color iconColor, char defaultEcho) {
        JPanel wrapper = new JPanel(new BorderLayout(0, 0));
        wrapper.setBackground(bg);
        wrapper.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(border, 1, true),
                new EmptyBorder(0, 0, 0, 0)
        ));
        

        field.setBackground(bg);
        field.setForeground(fg);
        field.setCaretColor(fg);
        field.setBorder(new EmptyBorder(6, 8, 6, 0)); // sağda boşluk bırak
        wrapper.add(field, BorderLayout.CENTER);
        
        // Göz ikonu butonu (sağ tarafta)
        final boolean[] isVisible = {false};
        EyeIcon eyeIcon = new EyeIcon(iconColor, isVisible);
        JButton toggleBtn = new JButton(eyeIcon);
        toggleBtn.setFocusPainted(false);
        toggleBtn.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        toggleBtn.setContentAreaFilled(false);
        toggleBtn.setOpaque(false);
        toggleBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        
        toggleBtn.addActionListener(e -> {
            isVisible[0] = !isVisible[0];
            field.setEchoChar(isVisible[0] ? (char) 0 : defaultEcho);
            toggleBtn.repaint(); // ikonu yeniden çiz
        });
        
        wrapper.add(toggleBtn, BorderLayout.EAST);
        return wrapper;
    }
    

    /**
     * Yuvarlatılmış köşeli şeffaf panel - arkasındaki yıldızlar görünür
     */
    private static class RoundedPanel extends JPanel {
        private final int radius;
        private final Color backgroundColor;
        
        public RoundedPanel(int radius, Color backgroundColor) {
            this.radius = radius;
            this.backgroundColor = backgroundColor;
            setOpaque(false);
        }
        
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(backgroundColor);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);
            g2.dispose();
        }
    }

    private static class EyeIcon implements Icon {
        private final Color color;
        private final boolean[] isVisible;
        private static final int SIZE = 20;
        
        public EyeIcon(Color color, boolean[] isVisible) {
            this.color = color;
            this.isVisible = isVisible;
        }
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.setStroke(new BasicStroke(1.5f));
            
            int centerX = x + SIZE / 2;
            int centerY = y + SIZE / 2;
            
            if (isVisible[0]) {
                g2.drawOval(x + 2, y + 4, SIZE - 4, SIZE - 8);
                g2.fillOval(centerX - 3, centerY - 3, 6, 6);
            } else {
                g2.drawOval(x + 2, y + 4, SIZE - 4, SIZE - 8);
                g2.drawLine(x + 4, centerY, x + SIZE - 4, centerY);
            }
            g2.dispose();
        }
        @Override
        public int getIconWidth() {
            return SIZE;
        }
        
        @Override
        public int getIconHeight() {
            return SIZE;
        }
    }

    /**
     * Starfield efekti için panel - yıldızları merkezden dışarı doğru animasyonlu çizer
     */
    private static class StarfieldPanel extends JPanel {
        private final List<Star> stars = new ArrayList<>();
        private final int numStars = 200;
        private Timer animationTimer;
        private int centerX, centerY;
        
        public StarfieldPanel() {
            // Koyu tema arka plan
            setBackground(new Color(18, 18, 18));
            initializeStars();
        }
        
        private void initializeStars() {
            for (int i = 0; i < numStars; i++) {
                stars.add(new Star());
            }
        }
        
        public void startAnimation() {
            // 60 FPS için ~16.67ms delay
            int delay = 1000 / 60;
            animationTimer = new Timer(delay, e -> {
                updateStars();
                repaint();
            });
            animationTimer.start();
        }
        
        public void stopAnimation() {
            if (animationTimer != null) {
                animationTimer.stop();
            }
        }
        
        private void updateStars() {
            Dimension size = getSize();
            if (size.width == 0 || size.height == 0) return;
            
            centerX = size.width / 2;
            centerY = size.height / 2;
            
            for (Star star : stars) {
                // Z değerini azalt (bize doğru geliyor)
                star.z -= star.speed;
                
                // Yıldız ekranın dışına çıktığında sıfırla
                if (star.z <= 0) {
                    star.reset(centerX, centerY);
                }
            }
        }
        
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            
            Dimension size = getSize();
            if (size.width == 0 || size.height == 0) return;
            
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            centerX = size.width / 2;
            centerY = size.height / 2;
            
            for (Star star : stars) {
                // Perspektif projeksiyon: 3D'den 2D'ye dönüştür
                float perspective = 200.0f / star.z; // 200.0f = görüş mesafesi
                
                // Ekranda gösterilecek x, y pozisyonları
                int screenX = (int) (centerX + star.x * perspective);
                int screenY = (int) (centerY + star.y * perspective);
                
                // Yıldız ekranın dışındaysa çizme
                if (screenX < 0 || screenX >= size.width || 
                    screenY < 0 || screenY >= size.height) {
                    continue;
                }
                
                // Z değerine göre parlaklık ve boyut hesapla (uzaktaki yıldızlar küçük ve soluk)
                float brightness = Math.min(1.0f, perspective);
                int starSize = Math.max(1, (int) (2 * perspective));
                
                // Parlaklığa göre renk ayarla - koyu tema için beyaz/açık renk
                int alpha = (int) (brightness * 255);
                // Beyaz renk kullan, parlaklığa göre alpha değeri ayarla
                Color starColor = new Color(255, 255, 255, alpha);
                
                g2.setColor(starColor);
                g2.fillOval(screenX - starSize / 2, screenY - starSize / 2, starSize, starSize);
            }
            
            g2.dispose();
        }
    }
    
    /**
     * Yıldız sınıfı - 3D koordinatları ve hız bilgisi içerir
     */
    private static class Star {
        float x, y, z;  // 3D koordinatlar
        float speed;    // Hız (z ekseninde)
        private final Random random = new Random();
        
        public Star() {
            reset(0, 0);
        }
        
        public void reset(int centerX, int centerY) {
            // Rastgele başlangıç pozisyonu (merkez etrafında)
            x = (random.nextFloat() - 0.5f) * 1000;
            y = (random.nextFloat() - 0.5f) * 1000;
            z = random.nextFloat() * 1000 + 1; // 1'den başla (0'dan uzak)
            
            // Rastgele hız (0.5 - 2.0 arası)
            speed = random.nextFloat() * 1.5f + 0.5f;
        }
    }
}
