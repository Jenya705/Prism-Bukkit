package me.botsko.prism.database;

import me.botsko.prism.api.actions.Handler;

import java.io.IOException;
import java.sql.SQLException;

/**
 * Created for use for the Add5tar MC Minecraft server
 * Created by benjamincharlton on 1/06/2019.
 */
public interface InsertQuery {

    void insert(Handler[] handlers) throws SQLException, IOException;

}
