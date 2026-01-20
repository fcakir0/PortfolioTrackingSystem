package service;

import dao.UserDao;
import model.User;

public class AuthService {

    private final UserDao userDao = new UserDao();

    public User login(String username, String password) {
        return userDao.findByUsernameAndPassword(username, password);
    }
}
