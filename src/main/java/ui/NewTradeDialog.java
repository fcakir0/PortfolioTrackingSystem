package ui;

import com.formdev.flatlaf.FlatClientProperties;
import dao.AssetDao;
import dao.TradeDao;
import model.Asset;
import model.Trade;
import model.TradeType;
import model.User;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import java.awt.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.function.Consumer;

public class NewTradeDialog extends JDialog {

    // Koyu tema renkleri
    private static final Color BG = new Color(45, 45, 45); // Koyu gri arka plan
    private static final Color PANEL_BG = new Color(55, 55, 55); // Daha açık koyu gri paneller
    private static final Color BORDER = new Color(80, 80, 80); // Açık gri border
    private static final Color TEXT = new Color(220, 220, 220); // Açık metin
    private static final Font FONT_REGULAR = new Font("Segoe UI", Font.PLAIN, 13);
    private static final Font FONT_BOLD = new Font("Segoe UI", Font.BOLD, 13);

    public NewTradeDialog(JFrame owner, User user, Consumer<Void> onSaved) {
        super(owner, "Yeni İşlem", true);
        setUndecorated(true); // İşletim sistemi başlık çubuğunu kaldır
        setSize(520, 320);
        setLocationRelativeTo(owner);

        // Koyu tema renkleri
        Color DARK_HEADER = new Color(35, 35, 35); // Daha koyu başlık çubuğu
        Color DARK_BORDER = new Color(80, 80, 80);

        // Ana içerik paneli
        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(BG);
        root.setBorder(new LineBorder(DARK_BORDER, 1));
        
        // Özel başlık çubuğu (sürüklenebilir)
        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setBackground(DARK_HEADER);
        titleBar.setPreferredSize(new Dimension(0, 35));
        titleBar.setBorder(new EmptyBorder(0, 10, 0, 5));
        
        // Dialog'u sürüklenebilir yap
        final int[] dragOffset = new int[2];
        titleBar.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                dragOffset[0] = e.getX();
                dragOffset[1] = e.getY();
            }
        });
        titleBar.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseDragged(java.awt.event.MouseEvent e) {
                Point currentLocation = getLocation();
                setLocation(
                    currentLocation.x + e.getX() - dragOffset[0],
                    currentLocation.y + e.getY() - dragOffset[1]
                );
            }
        });
        
        // Başlık
        JLabel titleLabel = new JLabel("Yeni İşlem");
        titleLabel.setForeground(TEXT);
        titleLabel.setFont(FONT_BOLD);
        titleBar.add(titleLabel, BorderLayout.WEST);
        
        // Kapatma butonu (X) - özel çizim ile
        final Color[] closeButtonColor = {TEXT}; // Hover için renk değişkeni
        JButton btnClose = new JButton() {
            @Override
            protected void paintComponent(Graphics g) {
                if (isOpaque()) {
                    super.paintComponent(g);
                }
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                int size = 12;
                int x = (getWidth() - size) / 2;
                int y = (getHeight() - size) / 2;
                
                g2.setColor(closeButtonColor[0]);
                g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                // X çizgileri
                g2.drawLine(x, y, x + size, y + size);
                g2.drawLine(x + size, y, x, y + size);
                
                g2.dispose();
            }
        };
        btnClose.setBackground(DARK_HEADER);
        btnClose.setBorderPainted(false);
        btnClose.setFocusPainted(false);
        btnClose.setOpaque(false);
        btnClose.setPreferredSize(new Dimension(30, 30));
        btnClose.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnClose.addActionListener(e -> dispose());
        
        // Hover efekti
        btnClose.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                btnClose.setBackground(new Color(60, 60, 60));
                btnClose.setOpaque(true);
                closeButtonColor[0] = Color.WHITE; // Hover'da beyaz
                btnClose.repaint();
            }
            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                btnClose.setBackground(DARK_HEADER);
                btnClose.setOpaque(false);
                closeButtonColor[0] = TEXT; // Normal durumda açık gri
                btnClose.repaint();
            }
        });
        
        titleBar.add(btnClose, BorderLayout.EAST);
        
        // İçerik paneli
        JPanel innerContent = new JPanel(new BorderLayout(10, 10));
        innerContent.setBorder(new EmptyBorder(12, 12, 12, 12));
        innerContent.setBackground(BG);

        AssetDao assetDao = new AssetDao();
        List<Asset> assets = assetDao.findAll();

        // --- Form bileşenleri ---
        JComboBox<model.MarketType> cbMarketType = new JComboBox<>(model.MarketType.values());

        // Varlık ismi artık serbest metin alanı
        JTextField tfAssetName = new JTextField();
        styleField(tfAssetName);

        JComboBox<TradeType> cbType = new JComboBox<>(TradeType.values());
        JTextField tfQty = new JTextField();
        JTextField tfPrice = new JTextField();
        JTextField tfDate = new JTextField(LocalDate.now().toString()); // YYYY-MM-DD

        // Fiyat alanına sadece rakam ve nokta girişi
        ((AbstractDocument) tfPrice.getDocument()).setDocumentFilter(new NumericDocumentFilter());
        // Adet alanına rakam ve nokta girişi (ondalıklı değerler için, örn. kripto: 3.5)
        ((AbstractDocument) tfQty.getDocument()).setDocumentFilter(new NumericDocumentFilter());
        // Varlık ismine sadece büyük harf ve rakam girişi
        ((AbstractDocument) tfAssetName.getDocument()).setDocumentFilter(new UpperAlnumFilter());

        // Market değiştikçe varlıkları filtrelemek istersen burada filtreleyebilirsin
        cbMarketType.addActionListener(e -> {
            // Şimdilik yalnızca seçili market bilgisini güncel tutuyoruz.
            // Kayıt sırasında bu bilgi ile varlık ismini eşleyeceğiz.
        });
        // ilk açılışta tetikle
        if (cbMarketType.getItemCount() > 0) {
            cbMarketType.setSelectedIndex(0);
        }

        // --- Form paneli ---
        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(PANEL_BG);
        form.setBorder(new LineBorder(BORDER, 1, true));

        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(6, 10, 6, 10);
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.gridx = 0;
        gc.gridy = 0;

        JLabel lblMarketType = new JLabel("Varlık Çeşidi:");
        styleLabel(lblMarketType);
        form.add(lblMarketType, gc);

        gc.gridx = 1;
        styleComboBox(cbMarketType);
        form.add(cbMarketType, gc);

        gc.gridx = 0;
        gc.gridy++;
        JLabel lblAsset = new JLabel("Varlık Sembolü:");
        styleLabel(lblAsset);
        form.add(lblAsset, gc);

        gc.gridx = 1;
        form.add(tfAssetName, gc);

        gc.gridx = 0;
        gc.gridy++;
        JLabel lblType = new JLabel("İşlem Tipi:");
        styleLabel(lblType);
        form.add(lblType, gc);

        gc.gridx = 1;
        styleComboBox(cbType);
        // BUY/SELL yerine "Alış" / "Satış" göster
        cbType.setRenderer((list, value, index, isSelected, cellHasFocus) -> {
            String text = "";
            if (value == TradeType.BUY) text = "Alış";
            else if (value == TradeType.SELL) text = "Satış";
            JLabel lbl = new JLabel(text);
            lbl.setOpaque(true);
            lbl.setFont(FONT_REGULAR);
            lbl.setBackground(isSelected ? new Color(40, 120, 200) : PANEL_BG); // Koyu mavi seçim
            lbl.setForeground(TEXT);
            return lbl;
        });
        form.add(cbType, gc);

        gc.gridx = 0;
        gc.gridy++;
        JLabel lblQty = new JLabel("Adet:");
        styleLabel(lblQty);
        form.add(lblQty, gc);

        gc.gridx = 1;
        styleField(tfQty);
        form.add(tfQty, gc);

        gc.gridx = 0;
        gc.gridy++;
        JLabel lblPrice = new JLabel("Fiyat:");
        styleLabel(lblPrice);
        form.add(lblPrice, gc);

        gc.gridx = 1;
        styleField(tfPrice);
        form.add(tfPrice, gc);

        gc.gridx = 0;
        gc.gridy++;
        JLabel lblDate = new JLabel("Tarih (YYYY-MM-DD):");
        styleLabel(lblDate);
        form.add(lblDate, gc);

        gc.gridx = 1;
        styleField(tfDate);
        form.add(tfDate, gc);

        // --- Kaydet butonu alanı ---
        JButton btnSave = new JButton("Kaydet");
        stylePrimaryButton(btnSave);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setBackground(BG);
        bottom.setBorder(new EmptyBorder(10, 0, 0, 0));
        bottom.add(btnSave, BorderLayout.CENTER);

        // --- Aksiyon ---
        btnSave.addActionListener(e -> {
            try {
                String assetNameOrSymbol = tfAssetName.getText().trim();
                if (assetNameOrSymbol.isEmpty()) {
                    showErrorDialog("Lütfen varlık ismini girin.");
                    return;
                }

                Object mtObj = cbMarketType.getSelectedItem();
                if (!(mtObj instanceof model.MarketType)) {
                    showErrorDialog("Lütfen varlık çeşidini seçin.");
                    return;
                }
                model.MarketType mt = (model.MarketType) mtObj;

                if (tfQty.getText().trim().isEmpty()) {
                    showErrorDialog("Lütfen adet girin.");
                    return;
                }
                if (tfPrice.getText().trim().isEmpty()) {
                    showErrorDialog("Lütfen fiyat girin.");
                    return;
                }

                double qty;
                double price;
                try {
                    qty = Double.parseDouble(tfQty.getText().trim());
                } catch (NumberFormatException nfe) {
                    showErrorDialog("Adet alanı sayısal olmalıdır.");
                    return;
                }
                try {
                    price = Double.parseDouble(tfPrice.getText().trim());
                } catch (NumberFormatException nfe) {
                    showErrorDialog("Fiyat alanı sayısal olmalıdır.");
                    return;
                }

                if (qty <= 0) {
                    showErrorDialog("Adet 0'dan büyük olmalıdır.");
                    return;
                }
                if (price <= 0) {
                    showErrorDialog("Fiyat 0'dan büyük olmalıdır.");
                    return;
                }

                LocalDate tradeDate;
                try {
                    tradeDate = LocalDate.parse(tfDate.getText().trim());
                } catch (Exception exDate) {
                    showErrorDialog("Tarih formatı hatalı. Örnek: 2025-12-16");
                    return;
                }

                String search = assetNameOrSymbol.trim().toUpperCase();

                final Asset[] selectedAsset = { null };

                // 1) Önce seçili market ile tam eşleşme ara
                for (Asset candidate : assets) {
                    String sym = candidate.getSymbol() != null ? candidate.getSymbol().trim().toUpperCase() : null;
                    String yahoo = candidate.getYahooSymbol() != null ? candidate.getYahooSymbol().trim().toUpperCase() : null;
                    String name = candidate.getName() != null ? candidate.getName().trim().toUpperCase() : null;

                    boolean symbolMatch = sym != null && sym.equals(search);
                    boolean yahooMatch = yahoo != null && yahoo.equals(search);
                    boolean nameMatch = name != null && name.equals(search);

                    if ((symbolMatch || yahooMatch || nameMatch) &&
                            candidate.getMarket() == mt) {
                        selectedAsset[0] = candidate;
                        break;
                    }
                }

                // 2) Hâlâ bulunamadıysa market filtresi olmadan dene (kullanıcı yanlış market seçmiş olabilir)
                if (selectedAsset[0] == null) {
                    for (Asset candidate : assets) {
                        String sym = candidate.getSymbol() != null ? candidate.getSymbol().trim().toUpperCase() : null;
                        String yahoo = candidate.getYahooSymbol() != null ? candidate.getYahooSymbol().trim().toUpperCase() : null;
                        String name = candidate.getName() != null ? candidate.getName().trim().toUpperCase() : null;

                        boolean symbolMatch = sym != null && sym.equals(search);
                        boolean yahooMatch = yahoo != null && yahoo.equals(search);
                        boolean nameMatch = name != null && name.equals(search);

                        if (symbolMatch || yahooMatch || nameMatch) {
                            selectedAsset[0] = candidate;
                            break;
                        }
                    }
                }
                if (selectedAsset[0] == null) {
                    showErrorDialog("Bu isim/simgele eşleşen varlık bulunamadı.");
                    return;
                }

                Asset a = selectedAsset[0];

                TradeType tradeType = (TradeType) cbType.getSelectedItem();

                // Satış işlemlerinde portföy kontrolü yap
                if (tradeType == TradeType.SELL) {
                    TradeDao tradeDao = new TradeDao();
                    java.util.List<TradeDao.PositionAgg> positions = tradeDao.getAggregatedPositions(user.getId());

                    double currentQty = positions.stream()
                            .filter(p -> p.assetId() == a.getId())
                            .mapToDouble(TradeDao.PositionAgg::netQty)
                            .findFirst()
                            .orElse(0.0);

                    if (currentQty <= 0) {
                        // Daha kısa ve tek satıra sığan mesaj
                        showErrorDialog("Portföyde olmayan varlığa satış yapılamaz.");
                        return;
                    }

                    if (qty > currentQty + 1e-8) {
                        showErrorDialog("Satış adedi, portföyünüzdeki mevcut adetten (" + currentQty + ") fazla olamaz.");
                        return;
                    }
                }

                // Kullanıcının girdiği tarihle, o anki saati birleştir
                LocalTime nowTime = LocalTime.now().withSecond(0).withNano(0);
                java.time.LocalDateTime tradeDateTime = java.time.LocalDateTime.of(tradeDate, nowTime);

                Trade t = new Trade();
                t.setUserId(user.getId());
                t.setAssetId(a.getId());
                t.setTradeType(tradeType);
                t.setQuantity(qty);
                t.setPrice(price);
                t.setTradeDate(tradeDateTime);

                new TradeDao().insert(t);

                dispose();
                if (onSaved != null) onSaved.accept(null);

            } catch (Exception ex) {
                showErrorDialog("Hatalı giriş: " + ex.getMessage());
            }
        });

        innerContent.add(form, BorderLayout.CENTER);
        innerContent.add(bottom, BorderLayout.SOUTH);
        
        // Başlık çubuğu ve içeriği birleştir
        root.add(titleBar, BorderLayout.NORTH);
        root.add(innerContent, BorderLayout.CENTER);
        
        setContentPane(root);
    }

    private void styleLabel(JLabel lbl) {
        lbl.setFont(FONT_BOLD);
        lbl.setForeground(TEXT);
    }

    private void styleField(JTextField tf) {
        tf.setFont(FONT_REGULAR);
        tf.setForeground(TEXT);
        tf.setBackground(new Color(65, 65, 65)); // Koyu tema için input arka plan
        tf.setCaretColor(TEXT);
        tf.setBorder(new LineBorder(BORDER, 1, true));
    }

    private void styleComboBox(JComboBox<?> cb) {
        cb.setFont(FONT_REGULAR);
        cb.setForeground(TEXT);
        cb.setBackground(new Color(65, 65, 65)); // Koyu tema için combobox arka plan
        cb.setBorder(new LineBorder(BORDER, 1, true));
        
        // Dropdown listesi için koyu tema renderer
        cb.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                c.setBackground(isSelected ? new Color(40, 120, 200) : new Color(65, 65, 65)); // Seçili: mavi, değil: koyu gri
                c.setForeground(TEXT);
                return c;
            }
        });
        
        // Dropdown popup'ının arka planını koyu yap
        cb.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e) {
                // Tüm açık popup'ları bul ve arka planını ayarla
                java.awt.Window[] windows = java.awt.Window.getWindows();
                for (java.awt.Window window : windows) {
                    if (window instanceof JWindow && window.getComponentCount() > 0) {
                        JWindow jWindow = (JWindow) window;
                        java.awt.Component comp = jWindow.getComponent(0);
                        if (comp instanceof JPopupMenu) {
                            JPopupMenu menu = (JPopupMenu) comp;
                            menu.setBackground(new Color(65, 65, 65));
                            for (int i = 0; i < menu.getComponentCount(); i++) {
                                java.awt.Component child = menu.getComponent(i);
                                if (child instanceof JList) {
                                    ((JList<?>) child).setBackground(new Color(65, 65, 65));
                                } else if (child instanceof JScrollPane) {
                                    JScrollPane scrollPane = (JScrollPane) child;
                                    scrollPane.getViewport().setBackground(new Color(65, 65, 65));
                                    java.awt.Component view = scrollPane.getViewport().getView();
                                    if (view instanceof JList) {
                                        ((JList<?>) view).setBackground(new Color(65, 65, 65));
                                    }
                                }
                            }
                        }
                    }
                }
            }
            @Override
            public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e) {}
            
            @Override
            public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e) {}
        });
    }

    private void stylePrimaryButton(JButton btn) {
        btn.setFont(FONT_BOLD);
        btn.setForeground(Color.WHITE);
        btn.setBackground(new Color(40, 120, 200)); // Koyu mavi
        btn.setFocusPainted(false);
        btn.setBorder(new EmptyBorder(10, 20, 10, 20)); // FlatLaf için daha iyi padding
        btn.setOpaque(true);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        // FlatLaf için buton tipi
        btn.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_BORDERLESS);
    }

    /**
     * Yalnızca 0-9 ve bir adet nokta (.) girişine izin veren basit filtre.
     */
    private static class NumericDocumentFilter extends DocumentFilter {
        @Override
        public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr)
                throws BadLocationException {
            if (string != null && isNumeric(string, fb.getDocument().getText(0, fb.getDocument().getLength()))) {
                super.insertString(fb, offset, string, attr);
            }
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
                throws BadLocationException {
            if (text != null && isNumeric(text, fb.getDocument().getText(0, fb.getDocument().getLength()))) {
                super.replace(fb, offset, length, text, attrs);
            } else if (text == null || text.isEmpty()) {
                super.replace(fb, offset, length, text, attrs);
            }
        }

        private boolean isNumeric(String text, String current) {
            String candidate = current + text;
            // Boş stringe izin ver
            if (candidate.isEmpty()) return true;
            int dotCount = 0;
            for (char c : candidate.toCharArray()) {
                if (c == '.') {
                    dotCount++;
                    if (dotCount > 1) return false;
                } else if (!Character.isDigit(c)) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * Yalnızca A-Z, 0-9 ve '.' girişine izin verir, girilen harfleri otomatik büyük yapar.
     */
    private static class UpperAlnumFilter extends DocumentFilter {
        @Override
        public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr)
                throws BadLocationException {
            if (string != null) {
                String upper = string.toUpperCase();
                if (upper.chars().allMatch(c -> Character.isDigit(c)
                        || (c >= 'A' && c <= 'Z')
                        || c == '.')) {
                    super.insertString(fb, offset, upper, attr);
                }
            }
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
                throws BadLocationException {
            if (text == null || text.isEmpty()) {
                super.replace(fb, offset, length, text, attrs);
            } else {
                String upper = text.toUpperCase();
                if (upper.chars().allMatch(c -> Character.isDigit(c)
                        || (c >= 'A' && c <= 'Z')
                        || c == '.')) {
                    super.replace(fb, offset, length, upper, attrs);
                }
            }
        }
    }

    /**
     * Hata mesaj dialog'u - uygulamanın açık temasına uygun
     */
    private void showErrorDialog(String message) {
        JDialog dialog = new JDialog(this, "Hata", true);
        dialog.setSize(400, 150);
        dialog.setLocationRelativeTo(this);
        dialog.setResizable(false);

        JPanel content = new JPanel(new BorderLayout(20, 20));
        content.setBackground(BG);
        content.setBorder(new EmptyBorder(20, 20, 20, 20));

        // Mesaj paneli
        JPanel messagePanel = new JPanel(new BorderLayout());
        messagePanel.setOpaque(false);

        // Hata ikonu (kırmızı daire içinde X)
        JLabel iconLabel = new JLabel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                int size = 40;
                int x = (getWidth() - size) / 2;
                int y = (getHeight() - size) / 2;
                
                // Kırmızı daire
                g2.setColor(new Color(178, 34, 34));
                g2.fillOval(x, y, size, size);
                
                // Beyaz X işareti
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int centerX = x + size / 2;
                int centerY = y + size / 2;
                int offset = 10;
                g2.drawLine(centerX - offset, centerY - offset, centerX + offset, centerY + offset);
                g2.drawLine(centerX + offset, centerY - offset, centerX - offset, centerY + offset);
                
                g2.dispose();
            }
        };
        iconLabel.setPreferredSize(new Dimension(60, 60));

        // Mesaj metni
        JLabel messageLabel = new JLabel("<html><div style='text-align: center;'>" + message + "</div></html>");
        messageLabel.setFont(FONT_REGULAR);
        messageLabel.setForeground(TEXT);
        messageLabel.setHorizontalAlignment(SwingConstants.CENTER);

        JPanel centerPanel = new JPanel(new BorderLayout(15, 0));
        centerPanel.setOpaque(false);
        centerPanel.add(iconLabel, BorderLayout.WEST);
        centerPanel.add(messageLabel, BorderLayout.CENTER);
        
        messagePanel.add(centerPanel, BorderLayout.CENTER);

        // Buton paneli
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        buttonPanel.setOpaque(false);

        JButton btnOK = new JButton("Tamam");
        btnOK.setPreferredSize(new Dimension(100, 35));
        btnOK.setFont(FONT_BOLD);
        btnOK.setForeground(Color.WHITE);
        btnOK.setBackground(new Color(178, 34, 34)); // Kırmızı
        btnOK.setFocusPainted(false);
        btnOK.setBorder(new EmptyBorder(10, 20, 10, 20));
        btnOK.setOpaque(true);
        btnOK.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnOK.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_BORDERLESS);

        btnOK.addActionListener(ev -> dialog.dispose());

        buttonPanel.add(btnOK);

        content.add(messagePanel, BorderLayout.CENTER);
        content.add(buttonPanel, BorderLayout.SOUTH);

        dialog.setContentPane(content);
        dialog.getRootPane().setDefaultButton(btnOK);
        dialog.setVisible(true);
    }
}
