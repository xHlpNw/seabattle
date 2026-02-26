package com.seabattle.bot;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.webapp.WebAppInfo;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.util.List;
import java.util.HashSet;
import java.util.Set;

public class BotApplication extends TelegramLongPollingBot {

    private final static String WEB_APP_URL = "https://battle-ship-pro.ru/";
    private final static WebAppInfo WEB_APP_INFO = new WebAppInfo(WEB_APP_URL);
    private final Set<Long> awaitingLinkUsers = new HashSet<>();

    public BotApplication() {
    }

    @Override
    public String getBotUsername() {
        return "@battle_ship_pro_bot";
    }

    @Override
    public String getBotToken() {
        return System.getenv("BOT_TOKEN");
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage()) {
            var msg = update.getMessage();
            var user = msg.getFrom();
            var userId = user.getId();
            if (msg.isCommand()) {
                switch (msg.getText()) {
                    case "/start" -> {
                        sendStartMessage(userId,
                                "Добро пожаловать, нажми на кнопку ниже, чтобы открыть приложение",
                                "Открыть приложение");
                    }
                    case "/game" -> {
                        awaitingLinkUsers.add(userId);
                        sendText(userId, "Пожалуйста, пришлите ссылку на игру для подключения");
                    }
                }
            } else if (awaitingLinkUsers.contains(userId)) {
                String url = msg.getText();
                if (url.startsWith("https://")) {
                    sendWebAppButton(userId, "Открыть игру", url);
                } else {
                    sendText(userId, "Пожалуйста, отправь корректную ссылку, начинающуюся с https://");
                }
                awaitingLinkUsers.remove(userId);
            }
        }
    }

    public void sendText(Long who, String what) {
        SendMessage sm = SendMessage.builder()
                .chatId(who.toString())
                .text(what)
                .build();
        try {
            execute(sm);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }

    public void sendWebAppButton(Long who, String textForButton, String url) {
        WebAppInfo customWebApp = new WebAppInfo(url);
        KeyboardButton button = KeyboardButton.builder()
                .text(textForButton)
                .webApp(customWebApp)
                .build();

        KeyboardRow row = new KeyboardRow();
        row.add(button);

        ReplyKeyboardMarkup keyboard = ReplyKeyboardMarkup.builder()
                .keyboard(List.of(row))
                .resizeKeyboard(true)
                .oneTimeKeyboard(true)
                .build();

        SendMessage sm = SendMessage.builder()
                .chatId(who.toString())
                .text("Нажми на кнопку ниже, чтобы открыть игру")
                .replyMarkup(keyboard)
                .build();

        try {
            execute(sm);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }


    public void sendStartMessage(Long who, String what, String textForButton) {
        KeyboardButton button = KeyboardButton.builder()
                .text(textForButton)
                .webApp(WEB_APP_INFO)
                .build();

        KeyboardRow row = new KeyboardRow();
        row.add(button);

        ReplyKeyboardMarkup keyboard = ReplyKeyboardMarkup.builder()
                .keyboard(List.of(row))
                .resizeKeyboard(true)
                .oneTimeKeyboard(true)
                .build();

        SendMessage sm = SendMessage.builder()
                .chatId(who.toString())
                .text(what)
                .replyMarkup(keyboard)
                .build();

        try {
            execute(sm);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) throws TelegramApiException {
        TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
        BotApplication bot = new BotApplication();
        botsApi.registerBot(bot);
    }
}
