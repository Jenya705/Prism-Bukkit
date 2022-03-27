package me.botsko.prism.database.sql;

import me.botsko.prism.Prism;
import me.botsko.prism.api.actions.Handler;
import me.botsko.prism.database.InsertQuery;
import me.botsko.prism.database.PrismDataSource;
import me.botsko.prism.database.QueryBuilder;
import me.botsko.prism.players.PlayerIdentification;
import me.botsko.prism.players.PrismPlayer;
import me.botsko.prism.utils.IntPair;
import me.botsko.prism.utils.block.Utilities;
import org.bukkit.Location;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;

/**
 * Created for use for the Add5tar MC Minecraft server
 * Created by Narimm on 1/06/2019.
 */
public class SqlInsertBuilder extends QueryBuilder implements InsertQuery {

    /**
     * Create an insert builder.
     * @param dataSource Data source
     */
    public SqlInsertBuilder(PrismDataSource dataSource) {
        super(dataSource);
    }

    @Override
    public void insert(Handler[] handlers) throws SQLException, IOException {
        try (
                Connection conn = dataSource.getConnection();
                PreparedStatement statement = conn.prepareStatement(getQuery(), Statement.RETURN_GENERATED_KEYS)
        ) {
            for (Handler handler: handlers) {
                if (handler == null) break;
                int worldId = 0;
                String worldName = handler.getLoc().getWorld().getName();
                if (Prism.prismWorlds.containsKey(worldName)) {
                    worldId = Prism.prismWorlds.get(worldName);
                }
                int actionId = 0;
                if (Prism.prismActions.containsKey(handler.getActionType().getName())) {
                    actionId = Prism.prismActions.get(handler.getActionType().getName());
                }
                PrismPlayer prismPlayer = PlayerIdentification.getPrismPlayerByNameFromCache(handler.getSourceName());
                int playerId = prismPlayer.getId();
                if (worldId == 0 || actionId == 0 || playerId == 0) {
                    Prism.debug("Sql data error: Handler:" + handler);
                }
                IntPair newIds = Prism.getItems().materialToIds(handler.getMaterial(),
                        Utilities.dataString(handler.getBlockData()));
                IntPair oldIds = Prism.getItems().materialToIds(handler.getOldMaterial(),
                        Utilities.dataString(handler.getOldBlockData()));
                Location l = handler.getLoc();
                applyToInsert(statement, handler, actionId, playerId, worldId, newIds, oldIds, l);
                statement.addBatch();
            }
            statement.executeUpdate();
            ResultSet generatedKeys = statement.getGeneratedKeys();
            int i = 0;
            try (PreparedStatement extraStatement = conn.prepareStatement(
                    "INSERT INTO `" + prefix + "data_extra` (data_id, data) VALUES (?, ?)")) {
                boolean needToExecute = false;
                while (generatedKeys.next()) {
                    Handler handler = handlers[i++];
                    if (handler == null) break;
                    if (handler.hasExtraData()) {
                        String data = handler.serialize();
                        if (data != null && !data.isEmpty()) {
                            extraStatement.setLong(1, generatedKeys.getLong(1));
                            extraStatement.setString(2, data);
                            extraStatement.addBatch();
                            needToExecute = true;
                        }
                    }
                }
                if (needToExecute) {
                    extraStatement.executeUpdate();
                }
            }
        }
    }

    private void applyToInsert(PreparedStatement s, Handler a, int actionId, int playerId, int worldId,
                               IntPair newIds, IntPair oldIds, Location l) throws SQLException {
        s.setLong(1, a.getUnixEpoch());
        s.setInt(2, actionId);
        s.setInt(3, playerId);
        s.setInt(4, worldId);
        s.setInt(5, newIds.first);
        s.setInt(6, newIds.second);
        s.setInt(7, oldIds.first);
        s.setInt(8, oldIds.second);
        s.setInt(9, l.getBlockX());
        s.setInt(10, l.getBlockY());
        s.setInt(11, l.getBlockZ());
    }

    private String getQuery() {

        return "INSERT INTO " + prefix
                + "data (epoch,action_id,player_id,world_id,block_id,block_subid,old_block_id,old_block_subid,"
                + "x,y,z) VALUES (?,?,?,?,?,?,?,?,?,?,?)";
    }
}
