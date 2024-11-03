Per far partire il progetto una volta scaricato si hanno diverse classi: la suddivisione rispetta il patter MVC (Model View Control).
La run viene fatta partire nella classe StartApp che si trova al di fuori di tutte le directory.
Quando parte di aprono 5 finestre: 3 client, 1 Server con i log relativi alle azioni di nuove mail o eliminazione una finestra che permette con un tasto di aprire dei client se per sbaglio vengono chiusi. 
Nella directory spam c'è una cosa in più: è una classe che con un tasto scrive da 1 a 4 mail in contemporenea a 1 client destinatario (per far vedere la mutua esclusione).
Domande possibili con risposte su codice:
1. Dove e come viene implementata la mutua esclusione?
   La mutua esclusione viene implementata in diverse posioni di codice nel progetto seguendo determinati meccanismi:
   ---- **ClientController.java** ----
   private final Object lock = new Object();
   private final Object emailOperationLock = new Object();
   // Lock su emailOperationLock per operazioni email
   private synchronized boolean sendEmail(Email email) {
    synchronized (emailOperationLock) {
        // Protegge tutte le operazioni di invio email
        // Impedisce invii simultanei dalla stessa istanza client
    }
   }
   // Lock su emailOperationLock per fetch
   private synchronized void fetchNewEmails() {
    synchronized (emailOperationLock) {
        // Protegge le operazioni di recupero email
        // Impedisce fetch simultanei dallo stesso client
    }
   }
   In questo caso viene fatto utilizzo di un lock per l'invio e l'arrivo di mail anche da più destinatari in contemporanea.

   ---- **MailServer.java** ----
   private final Object accountLock = new Object();
   private final Object emailLock = new Object();

   public void sendEmail(Email email) {
     synchronized (emailLock) {          // Lock esterno
       synchronized (accountLock) {    // Lock interno
         // Lock gerarchico: prima emailLock poi accountLock
         // Previene deadlock nelle operazioni sugli account
       }
      }
   }
   public List<Email> getNewEmails(String recipient) {
     synchronized (emailLock) {      // Lock esterno
       synchronized (accountLock) {  // Lock interno
             // Stesso ordine gerarchico dei lock
            // Garantisce consistenza nella lettura
        }
     }
   }

  ---- **EmailFileManager.java** ----
  // Lock implicito sul metodo
  public static synchronized int getNextId() {
    // Lock sull'oggetto classe EmailFileManager
    // Garantisce unicità degli ID generati
    // Protegge l'accesso concorrente al contatore
    int currentId = idCounter.get();
    int nextId = currentId + 1;
    idCounter.set(nextId);
    updateIdFile(nextId);
    return nextId;
  }
  
  // Uso di AtomicInteger per il contatore
  private static final AtomicInteger idCounter = new AtomicInteger(0);
    // Fornisce operazioni atomiche sul contatore
    // Non richiede lock esplicito
    
----------- **_Teoria_** -----------
Tipi di lock utilizzati:                                            Ogni tipo di lock serve uno scopo specifico:

Lock su oggetti dedicati (lock, emailOperationLock)                 Lock dedicati: proteggono operazioni specifiche
Lock gerarchici (emailLock, accountLock)                            Lock gerarchici: prevengono deadlock
Lock impliciti su metodi (synchronized)                             Lock su metodi: proteggono l'intero scope del metodo
Lock atomici (AtomicInteger)                                        Lock atomici: garantiscono atomicità delle operazioni

I lock in informatica sono meccanismi di sincronizzazione che servono a regolare l'accesso alle risorse condivise tra più thread o processi. Ecco le caratteristiche principali:
- Un lock è una variabile di sincronizzazione che può essere in due stati: libero o occupato
- Solo un thread alla volta può possedere il lock
- Gli altri thread che richiedono il lock vengono messi in attesa

Scopi principali:                  Caratteristiche importanti:

Mutua esclusione                   - Atomicità delle operazioni
Sincronizzazione                   - Visibilità delle modifiche tra thread
Protezione dati condivisi          - Ordine di acquisizione per evitare deadlock
Prevenzione race condition         - Granularità del lock
I lock sono fondamentali nella programmazione concorrente per garantire la correttezza e la consistenza delle operazioni su risorse condivise.

2. Come avviene la gestione delle mail quando il Server cade?
   La gestione delle mail durante la caduta del server viene gestita principalmente attraverso due meccanismi:
   a. Persistenza locale delle email in ClientController:                    ---- **ClientController.java**  ----
     private void loadEmailsFromDisk() {
      executorService.submit(() -> {
        try {
            String userEmail = mailbox.getEmailAddress();
            List<Email> loadedEmails = EmailFileManager.loadEmails(userEmail);
            Platform.runLater(() -> {
                mailbox.clearEmails();
                for (Email email : loadedEmails) {
                    mailbox.addReceivedEmail(email);
                }
                emailTableView.refresh();
            });
        } catch (IOException e) {
            Platform.runLater(() -> showErrorAlert("Load Error", "Failed to load emails from disk"));
        }
    });
   }
   b. Verifica continua dello stato del server:                     ---- **ClientController.java**  ----
     private void checkConnection() {
    Socket socket = null;
    try {
        socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
        NetworkUtils.sendObject(socket, "PING");
        String response = (String) NetworkUtils.receiveObject(socket);
        boolean isConnected = "PONG".equals(response);
        Platform.runLater(() -> {
            connectedProperty.set(isConnected);
        });
    } catch (Exception e) {
        Platform.runLater(() -> {
            connectedProperty.set(false);
        });
    }
   }
   c. Sistema di notifica stato connessione:                                ---- **ClientController.java**  ----
     connectedProperty.addListener((observable, oldValue, newValue) -> {
       connectionStatusLabel.setText(newValue ? "Connected" : "Disconnected");
       connectionStatusLabel.setStyle(newValue ? "-fx-text-fill: green;" : "-fx-text-fill: red;");
     });
   d. Gestione del salvataggio email in EmailFileManager:                    ---- **EmailFileManager.java**  ----
     public static void saveEmail(Email email, String userEmail) throws IOException {
       Path userDir = Paths.get(BASE_DIR, userEmail);
       Files.createDirectories(userDir);
       String fileName = "Email_" + email.getId() + ".txt";
       Path filePath = userDir.resolve(fileName);
       try (BufferedWriter writer = Files.newBufferedWriter(filePath)) {
        // Salvataggio dei dati email
       }
   }
   
Il sistema funziona così:
1. Le email vengono salvate localmente su disco
2. Il client verifica periodicamente la connessione col server
3. L'interfaccia mostra lo stato della connessione
4. Quando il server è offline:
    - Le email rimangono accessibili localmente
    - Le operazioni di invio vengono bloccate
    - L'utente viene notificato dello stato disconnesso
5. Al ripristino della connessione:
    - Il client si riconnette automaticamente
    - Le operazioni di invio tornano disponibili
    - L'interfaccia si aggiorna mostrando lo stato connesso

3.Come avvengono gli aggiornamenti in casella in entrata?
  ---- **ClientController.java**  ----
  private void setupAutoRefresh() {
    autoRefreshTimeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
        if (isConnected()) {
            fetchNewEmails();
        }
    }));
    autoRefreshTimeline.setCycleCount(Timeline.INDEFINITE);
    autoRefreshTimeline.play();
  }
  private synchronized void fetchNewEmails() {
    synchronized (emailOperationLock) {
        Socket socket = null;
        try {
            socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
            NetworkUtils.sendObject(socket, "FETCH_NEW_EMAILS");
            NetworkUtils.sendObject(socket, mailbox.getEmailAddress());
            List<Email> newEmails = (List<Email>) NetworkUtils.receiveObject(socket);
            Platform.runLater(() -> {
                int newEmailCount = 0;
                for (Email email : newEmails) {
                    if (!deletedEmailIds.contains(email.getId()) &&
                            !mailbox.hasEmail(email.getId())) {
                        mailbox.addReceivedEmail(email);
                        newEmailCount++;
                    }
                }
                if (newEmailCount > 0) {
                    refreshEmailTable();
                    showInfoAlert("Nuova mail per: " + mailbox.getEmailAddress(),
                            "Ricevuta una nuova email");
                }
            });
        } catch (Exception e) {
            handleConnectionError(e);
        }
      }
    }
    private void refreshEmailTable() {
      Platform.runLater(() -> {
        emailTableView.setItems(null);
        emailTableView.setItems(mailbox.getAllEmails());
        emailTableView.refresh();
      });
    }
    ---- **Mailbox.java**  ----
    public synchronized void addReceivedEmail(Email email) {
      if (!receivedEmails.contains(email)) {
        receivedEmails.add(email);
      }
    }

  public ObservableList<Email> getAllEmails() {
    ObservableList<Email> allEmails = FXCollections.observableArrayList();
    allEmails.addAll(receivedEmails);
    allEmails.addAll(sentEmails);
    return allEmails;
  }
  
Il processo di aggiornamento funziona così:                    Il sistema garantisce:

1. Timeline esegue controlli periodici ogni secondo            - Aggiornamenti in tempo reale
2. Se c'è connessione, richiede nuove email al server          - Gestione concorrente sicura
3. Le nuove email vengono aggiunte alla mailbox locale         - Notifiche immediate
4. L'interfaccia utente viene aggiornata                       - Persistenza dei dati
5. L'utente riceve una notifica per le nuove email             - Consistenza della visualizzazione

4. Come viene gestito l'errore di sintassi nel progetto?
   ---- **ClientController.java** ----
   private synchronized void handleSendEmail() {
        if (!isConnected()) {
            showServerClosedAlert();
            return;
        }
        if (validateFields()) {
            String recipientsString = toField.getText().trim();
            List<String> recipients = Arrays.asList(recipientsString.split("\\s*,\\s*"));
            List<String> syntaxErrors = new ArrayList<>();
            List<String> nonExistentAddresses = new ArrayList<>();
            **for (String recipient : recipients) {
                if (!recipient.endsWith("@progetto.com")) {
                    syntaxErrors.add(recipient);
                } else if (!isValidRecipient(recipient)) {
                    nonExistentAddresses.add(recipient);
                }
            }
            if (!syntaxErrors.isEmpty()) {
                StringBuilder errorMessage = new StringBuilder();
                errorMessage.append("I seguenti indirizzi non hanno il formato corretto:\n\n");
                for (String error : syntaxErrors) {
                    errorMessage.append("• ").append(error)
                            .append(" → formato corretto richiesto: \n nomeutente@progetto.com\n");
                }
                showErrorAlert("Errore di sintassi email", errorMessage.toString());
                return;
            }**
            if (!nonExistentAddresses.isEmpty()) {
                StringBuilder errorMessage = new StringBuilder();
                errorMessage.append("I seguenti indirizzi non sono registrati nel sistema:\n\n");
                for (String address : nonExistentAddresses) {
                    errorMessage.append("• ").append(address).append("\n");
                }
                errorMessage.append("\nVerifica che gli indirizzi siano presenti in emails.txt");
                showErrorAlert("Indirizzi non trovati", errorMessage.toString());
                return;
            }
           ...
     }
   }
   In questi due casi viene prima controllato in ClientController.java se il "dominio" @progetto.com sia giusto e poi se l'effettivo account esite: nel nostro caso filippoditto, fabiodelia, vanessaconterno.
L'utente riceve feedback immediato attraverso:
- Alert di errore con messaggio specifico
- Mantenimento del testo inserito per correzioni
- Possibilità di ritentare l'invio

5. REPLY / REPLY ALL:
     ---- **ClientController.java** ----
     @FXML
      private void handleReplyEmail() {
       Email selectedEmail = emailTableView.getSelectionModel().getSelectedItem();
        if (selectedEmail != null) {
          openComposeWindow(selectedEmail, "Reply");
        }
      }
     private void openComposeWindow(Email selectedEmail, String mode) {
      // ... setup della finestra di composizione
      switch (mode) {
        case "Reply":
            toField.setText(selectedEmail.getSender());
            subjectField.setText("Re: " + selectedEmail.getSubject());
            bodyArea.setText("\n\n----- Messaggio Originale -----\n" + selectedEmail.getBody());
            break;
        //...
      }
    }

   ---- **MailServer.java** ----
   public void sendEmail(Email email) {
    synchronized (emailLock) {
        String sender = email.getSender();
        List<String> recipients = email.getRecipients();
        synchronized (accountLock) {
            createAccount(sender);
            recipients.forEach(this::createAccount);
        }
        // ... gestione dell'invio
    }
   }
La reply viene trattata come una nuova email dal server, ma con riferimenti all'email originale (oggetto con "Re:", citazione del messaggio originale). Il flusso è:
1. Client: Utente seleziona email e clicca reply
2. Client: Prepara nuova email con campi pre-compilati
3. Client: Invia al server come normale email
4. Server: Processa come una normale email e la consegna al destinatario
   ---- **ClientController.java** ----
   @FXML
   private void handleReplyAllEmail() {
      Email selectedEmail = emailTableView.getSelectionModel().getSelectedItem();
      if (selectedEmail != null) {
        openComposeWindow(selectedEmail, "Reply All");
      }
    }
    private void openComposeWindow(Email selectedEmail, String mode) {
      // ... setup della finestra di composizione
      case "Reply All":
        List<String> allRecipients = new ArrayList<>(selectedEmail.getRecipients());
        allRecipients.add(selectedEmail.getSender());
        allRecipients.remove(mailbox.getEmailAddress()); // Rimuove l'utente corrente dalla lista
        toField.setText(String.join(", ", allRecipients));
        subjectField.setText("Re: " + selectedEmail.getSubject());
        bodyArea.setText("\n\n----- Original Message -----\n" + selectedEmail.getBody());
        break;
    }

    ----**MailServer.java**----
     // Gestione principale della Reply All
    public void sendEmail(Email email) {
    synchronized (emailLock) {
        String sender = email.getSender();
        List<String> recipients = email.getRecipients();
        // Creazione account per tutti i partecipanti
        synchronized (accountLock) {
            createAccount(sender);
            recipients.forEach(this::createAccount);
        }
        // Distribuzione dell'email a tutti i destinatari
        for (String recipient : recipients) {
            int uniqueEmailId = EmailFileManager.getNextId();
            // Creazione copia per ogni destinatario
            Email recipientCopy = createEmailCopy(email);
            recipientCopy.setId(uniqueEmailId);
            recipientCopy.setRecipients(email.getRecipients());
            // Salvataggio nella inbox del destinatario
            synchronized (accountLock) {
                accounts.get(recipient).addToInbox(recipientCopy);
            }
            // Persistenza su file
            try {
                EmailFileManager.saveEmail(recipientCopy, recipient);
            } catch (IOException e) {
                serverController.logEvent("Errore durante il salvataggio dell'email per " + recipient);
            }
          }
        }
      }

      // Metodo di supporto per la creazione delle copie
      private Email createEmailCopy(Email original) {
          Email copy = new Email();
          copy.setId(original.getId());
          copy.setSender(original.getSender());
          copy.setRecipients(new ArrayList<>(original.getRecipients()));
          copy.setSubject(original.getSubject());
          copy.setBody(original.getBody());
          copy.setSentDate(original.getSentDate());
          copy.setRead(false);
          return copy;
      }
La differenza chiave tra Reply e Reply All sta nella gestione dei destinatari:
  Reply: invia solo al mittente originale
  Reply All: invia a tutti i destinatari originali + il mittente originale, escludendo l'utente corrente

7. FORWARD:
     ---- **ClientController.java** ----
     @FXML
     private void handleForwardEmail() {
        Email selectedEmail = emailTableView.getSelectionModel().getSelectedItem();
      if (selectedEmail != null) {
        openComposeWindow(selectedEmail, "Forward");
      }
     }
   private void openComposeWindow(Email selectedEmail, String mode) {
    // ... setup della finestra di composizione
    case "Forward":
        toField.setText("");  // Campo destinatario vuoto per il forward
        subjectField.setText("Fwd: " + selectedEmail.getSubject());
        String forwardedContent = "\n\n----- Messaggio inoltrato -----\n" +
                "From: " + selectedEmail.getSender() + "\n" +
                "Date: " + selectedEmail.getSentDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) + "\n" +
                "Subject: " + selectedEmail.getSubject() + "\n" +
                "To: " + String.join(", ", selectedEmail.getRecipients()) + "\n\n" +
                selectedEmail.getBody();
        bodyArea.setText(forwardedContent);
        break;
   }

Le caratteristiche chiave del Forward sono:

1. Campo destinatario vuoto (l'utente deve specificare i nuovi destinatari)
2. Oggetto prefissato con "Fwd:"
3. Inclusione dei metadati completi dell'email originale (mittente, data, oggetto, destinatari)
4. Corpo del messaggio originale incluso con formattazione specifica

9. DELETE:
    ---- **ClientController.java** ----
    @FXML
   private void handleDeleteEmail() {
    Email selectedEmail = emailTableView.getSelectionModel().getSelectedItem();
    if (selectedEmail != null) {
        Alert confirmDelete = new Alert(Alert.AlertType.CONFIRMATION);
        confirmDelete.setTitle("Conferma eliminazione");
        confirmDelete.setHeaderText("Eliminare questa email?");
        confirmDelete.setContentText("Questa operazione non può essere annullata.");
        Optional<ButtonType> result = confirmDelete.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            deleteEmail(selectedEmail);
        }
    }
   }
   private void deleteEmail(Email email) {
    executorService.submit(() -> {
        try {
            if (EmailFileManager.deleteEmail(email.getId(), mailbox.getEmailAddress())) {
                Platform.runLater(() -> {
                    mailbox.removeEmail(email);
                    deletedEmailIds.add(email.getId());
                    refreshEmailTable();
                    returnToEmailListView();
                    showInfoAlert("Email eliminata", "L'email è stata eliminata con successo.");
                });
            }
        } catch (IOException e) {
            Platform.runLater(() -> {
                showErrorAlert("Errore", "Si è verificato un errore durante l'eliminazione dell'email: " + e.getMessage());
            });
        }
    });
   }

   ---- **MailServer.java**  ----
   public boolean deleteEmail(int emailId, String requestingUser) {
    synchronized (emailLock) {
        synchronized (accountLock) {
            EmailAccount account = accounts.get(requestingUser);
            if (account == null) {
                return false;
            }
            boolean deletedFromInbox = account.getInbox().removeIf(email -> email.getId() == emailId);
            boolean deletedFromSent = account.getSent().removeIf(email -> email.getId() == emailId);
            if (deletedFromInbox || deletedFromSent) {
                try {
                    EmailFileManager.deleteEmail(emailId, requestingUser);
                    return true;
                } catch (IOException e) {
                    e.printStackTrace();
                    return false;
                }
            }
            return false;
        }
      }
    }

   ---- **EmailFileManager.java** ----
   public static boolean deleteEmail(int emailId, String userEmail) throws IOException {
    Path userDir = Paths.get(BASE_DIR, userEmail);
    Path emailFile = userDir.resolve("Email_" + emailId + ".txt");

    if (Files.exists(emailFile)) {
        try {
            Files.delete(emailFile);
            deleteEmailFromAllRecipients(emailId);
            return true;
        } catch (IOException e) {
            System.err.println("Errore durante l'eliminazione del file: " + e.getMessage());
            return false;
        }
    }
    return false;
   }
   private static void deleteEmailFromAllRecipients(int emailId) {
    try {
        Path baseDir = Paths.get(BASE_DIR);
        if (Files.exists(baseDir)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(baseDir)) {
                for (Path userDir : stream) {
                    if (Files.isDirectory(userDir)) {
                        Path emailFile = userDir.resolve("Email_" + emailId + ".txt");
                        if (Files.exists(emailFile)) {
                            Files.delete(emailFile);
                        }
                    }
                }
            }
        }
    } catch (IOException e) {
        System.err.println("Errore durante l'eliminazione delle email per tutti i destinatari: " + e.getMessage());
    }
   }    
Il flusso di eliminazione è:

  1. Client mostra dialog di conferma
  2. Se confermato, richiede eliminazione al server
  3. Server rimuove l'email dalle liste in memoria (inbox e sent)
  4. Server elimina il file fisico dell'email
  5. Client aggiorna la sua vista rimuovendo l'email dalla tabella
     
11. Come avviene la comunicazione tra i Client e il Server?
    La comunicazione tra Client e Server avviene attraverso socket TCP/IP seguendo questo flusso:
    ---- **ClientController.java** ----
    // Invio email
    private synchronized boolean sendEmail(Email email) {
    synchronized (emailOperationLock) {
        Socket socket = null;
        try {
            socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
            NetworkUtils.sendObject(socket, "SEND_EMAIL");
            NetworkUtils.sendObject(socket, email);
            String response = (String) NetworkUtils.receiveObject(socket);
            // gestione risposta
        }
      }
    }

    // Verifica connessione
    public void checkConnection() {
      Socket socket = null;
      try {
          socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
          NetworkUtils.sendObject(socket, "PING");
          String response = (String) NetworkUtils.receiveObject(socket);
          boolean isConnected = "PONG".equals(response);
      }
    }

    ---- **ServerController.java**----
    private void acceptConnections() {
      while (isRunning) {
          try {
              Socket clientSocket = serverSocket.accept();
              executorService.submit(() -> handleClient(clientSocket));
          }
      }
    }

    private void handleClient(Socket clientSocket) {
      try {
          String command = (String) inputStream.readObject();
          switch (command) {
              case "SEND_EMAIL":
                  handleSendEmail(clientSocket);
                  break;
              case "FETCH_NEW_EMAILS":
                  handleFetchNewEmails(clientSocket);
                  break;
              case "PING":
                  NetworkUtils.sendObject(clientSocket, "PONG");
                  break;
          }
      }
    }

    ----**NetworkUtils.java** ----
    public static void sendObject(Socket socket, Object obj) throws IOException {
      if (socket.isClosed()) {
        throw new SocketException("Socket chiusa");
      }
      ObjectOutputStream out = getOutputStream(socket);
      out.writeObject(obj);
      out.flush();
    }

    public static Object receiveObject(Socket socket) throws IOException, ClassNotFoundException {
      if (socket.isClosed()) {
          throw new SocketException("Socket chiusa");
      }
      try {
          ObjectInputStream in = getInputStream(socket);
          return in.readObject();
      }
    }
---- **_Teoria_** ----
    Le socket sono endpoints di comunicazione che permettono lo scambio di dati tra processi attraverso la rete.
      Apertura Socket lato Server:
        // Creazione socket server
        ServerSocket serverSocket = new ServerSocket(PORT);
        // Accettazione connessione client
        Socket clientSocket = serverSocket.accept();
    Apertura Socket lato Client:
        // Creazione socket client
        Socket socket = new Socket("localhost", PORT);
    Chiusura Socket:
        // Chiusura sicura
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
Punti chiave:
1. Il Server crea una ServerSocket in ascolto su una porta
2. Il Client si connette specificando indirizzo e porta
3. La chiusura va sempre gestita in un blocco finally
4. È importante verificare che la socket non sia già chiusa
 
Le socket permettono:
- Comunicazione bidirezionale
- Trasmissione affidabile dei dati
- Connessioni TCP/IP
- Gestione di più client contemporaneamente
    
13. Come sono le Socket permanenti o no?
    Le socket in questo progetto non sono permanenti ma vengono create e chiuse per ogni singola operazione. Ecco il codice rilevante:
    ---- **ClientController.java** ----
    // Esempio di socket temporanea per invio email
    private synchronized boolean sendEmail(Email email) {
      Socket socket = null;
      try {
          socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
          NetworkUtils.sendObject(socket, "SEND_EMAIL");
          NetworkUtils.sendObject(socket, email);
          String response = (String) NetworkUtils.receiveObject(socket);
          return "OK".equals(response);
      } finally {
          if (socket != null && !socket.isClosed()) {
              socket.close();
          }
      }
    }
    ---- **ServerController.java**----
    // Il server accetta connessioni e crea nuove socket per ogni client
    private void acceptConnections() {
      while (isRunning) {
          Socket clientSocket = serverSocket.accept();
          executorService.submit(() -> handleClient(clientSocket));
      }
    }

    // Ogni socket client viene gestita e poi chiusa
    private void handleClient(Socket clientSocket) {
      try {
          // gestione richiesta
      } finally {
          if (!clientSocket.isClosed()) {
              clientSocket.close();
          }
      }
    }
Questo approccio è stato scelto per:
- Maggiore semplicità implementativa
- Minor consumo di risorse
- Migliore gestione degli errori di rete
- Nessuna necessità di mantenere connessioni persistenti
    
15. Come viene gestito il file di log del Server?
    Il file di log del Server viene gestito attraverso la TextArea nell'interfaccia grafica. Ecco il codice rilevante:
    ---- **ServerController.java** ----
    @FXML private TextArea logTextArea;

    public void logEvent(String message) {
      Platform.runLater(() -> logTextArea.appendText(message + "\n"));
    }
    Il log registra diversi tipi di eventi:
    1. Operazioni di avvio/arresto:
        // Avvio server
        logEvent("Server avviato sulla porta " + port);
        // Arresto server
        logEvent("Server stopped");
    2. Operazioni email:
        // Log invio email
        logEvent("Email inviate da: " + sender + " a: " + recipientsStr);
        // Log eliminazione email
        logEvent("Email " + emailId + " eliminata da: " + requestingUser);
    3. Errori e problemi:
        // Log errori connessione
        logEvent("Errore durante l'accettazione della connessione client: " + e.getMessage());
        // Log errori operazioni
        logEvent("Errore nella connessione del client: " + e.getMessage());
  Questo sistema permette:
  
  - Monitoraggio in tempo reale delle operazioni
  - Tracciamento degli errori
  - Visualizzazione dello stato del server
  - Debug delle operazioni client/server

    
17. Come sono legate le viste e i model?
    Le viste e i model sono legati attraverso il pattern MVC (Model-View-Controller) utilizzando il data binding di JavaFX. Ecco i principali collegamenti:
    ---- **ClientController.java** ----
    // Collegamento TableView con il model Mailbox
    @FXML private TableView<Email> emailTableView;
    private final Mailbox mailbox;

    @FXML
    public void initialize() {
        // Binding delle colonne con le proprietà del model Email
        senderColumn.setCellValueFactory(new PropertyValueFactory<>("sender"));
        subjectColumn.setCellValueFactory(new PropertyValueFactory<>("subject"));
        dateColumn.setCellValueFactory(cellData -> {
            Email email = cellData.getValue();
            String formattedDate = email.getSentDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            return javafx.beans.binding.Bindings.createStringBinding(() -> formattedDate);
        });
        // Collegamento TableView con la lista di email del Mailbox
        emailTableView.setItems(mailbox.getAllEmails());
    }

    ---- **Mailbox.java** ----
    // Observable collections per aggiornamento automatico della vista
    private ObservableList<Email> receivedEmails;
    private ObservableList<Email> sentEmails;
    
    public ObservableList<Email> getAllEmails() {
        ObservableList<Email> allEmails = FXCollections.observableArrayList();
        allEmails.addAll(receivedEmails);
        allEmails.addAll(sentEmails);
        return allEmails;
    }
**!!!!**  _Non c'è una comunicazione diretta tra viste e model. La comunicazione avviene sempre attraverso il Controller che funge da intermediario, rispettando il pattern MVC._  **!!!!**
    Vista → Controller → Model                             Model → Controller → Vista:

19. Sono state usate delle properties?
    Sì, nel progetto sono state utilizzate delle properties di JavaFX! 
    ---- **ClientController.java** ----
      // Property per stato connessione
      private final BooleanProperty connectedProperty;
      
      // Property per binding dimensioni UI
      composeView.prefWidthProperty().bind(detailOrComposeStack.widthProperty());
      composeView.prefHeightProperty().bind(detailOrComposeStack.heightProperty());
    ---- **Email.java** ----
    // Properties per i campi email
      private StringProperty sender;
      private StringProperty subject;
      private ObjectProperty<LocalDateTime> sentDate;
      private BooleanProperty read;
    ---- **Mailbox.java** ----
      // Properties per le collezioni di email
      private ListProperty<Email> receivedEmails;
      private ListProperty<Email> sentEmails;

Le properties sono utilizzate principalmente per:
- Stato dell'applicazione (connessione)
- Layout UI (dimensioni)
- Dati del modello (email)
- Collezioni osservabili (mailbox)

21. Sono stati usati degli observable list?
    Sì! Nel progetto sono state utilizzate diverse ObservableList, ecco i dettagli:
    ---- **Mailbox.java** ----
    // Liste osservabili per le email
    private ObservableList<Email> receivedEmails = FXCollections.observableArrayList();
    private ObservableList<Email> sentEmails = FXCollections.observableArrayList();
    
    public ObservableList<Email> getAllEmails() {
        return FXCollections.observableArrayList(
            Stream.concat(receivedEmails.stream(), sentEmails.stream())
                  .collect(Collectors.toList())
        );
    }
  ---- **ClientController.java** ----
    @FXML
    private TableView<Email> emailTableView;
    
    public void initialize() {
        // Binding della TableView con ObservableList
        emailTableView.setItems(mailbox.getAllEmails());
        // Listener per aggiornamenti
        mailbox.getReceivedEmails().addListener((ListChangeListener<Email>) change -> {
            while (change.next()) {
                if (change.wasAdded()) {
                    // Gestione nuove email
                }
            }
        });
    }
  Le ObservableList vengono utilizzate per:
    - Gestione delle email ricevute
    - Gestione delle email inviate
    - Aggiornamento automatico delle TableView
    - Notifiche di cambiamenti nella collezione

  Questo approccio garantisce:
    - Aggiornamenti UI in tempo reale
    - Sincronizzazione automatica tra model e view
    - Gestione efficiente delle collezioni di dati
