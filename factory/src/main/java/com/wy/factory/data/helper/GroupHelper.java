package com.wy.factory.data.helper;

import com.raizlabs.android.dbflow.sql.language.Join;
import com.raizlabs.android.dbflow.sql.language.SQLite;
import com.wy.common.factory.data.DataSource;
import com.wy.factory.Factory;
import com.wy.factory.R;
import com.wy.factory.model.api.RspModel;
import com.wy.factory.model.api.group.GroupCreateModel;
import com.wy.factory.model.card.GroupCard;
import com.wy.factory.model.card.GroupMemberCard;
import com.wy.factory.model.db.Group;
import com.wy.factory.model.db.GroupMember;
import com.wy.factory.model.db.GroupMember_Table;
import com.wy.factory.model.db.Group_Table;
import com.wy.factory.model.db.User;
import com.wy.factory.model.db.User_Table;
import com.wy.factory.model.db.view.MemberUserModel;
import com.wy.factory.net.Network;
import com.wy.factory.net.RemoteService;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/* 名称: ITalker.com.wy.factory.data.helper.GroupHelper
 * 用户: _VIEW
 * 时间: 2019/9/17,17:38
 * 描述: 群聊的简单辅助工具类
 */
public class GroupHelper {
    //查询群的信息，先本地后网络
    public static Group find(String groupId) {
        Group group = findFromLocal(groupId);
        if (group == null)
            group = findFromNet(groupId);
        return group;
    }

    //从本地找
    public static Group findFromLocal(String groupId) {
        return SQLite.select().from(Group.class)
                .where(Group_Table.id.eq(groupId))
                .querySingle();
    }

    //从网络找
    public static Group findFromNet(String groupId) {
        RemoteService remoteService = Network.remote();
        try {
            Response<RspModel<GroupCard>> response = remoteService.groupFind(groupId).execute();
            GroupCard card = response.body().getResult();
            if (card != null) {
                // 数据库的存储并通知
                Factory.getGroupCenter().dispatch(card);
                User user = UserHelper.search(card.getOwnerId());
                if (user != null) {
                    return card.build(user);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    //群的创建
    public static void create(GroupCreateModel model, final DataSource.Callback<GroupCard> callback) {
        RemoteService service = Network.remote();
        service.groupCreate(model).enqueue(new Callback<RspModel<GroupCard>>() {
            @Override
            public void onResponse(Call<RspModel<GroupCard>> call, Response<RspModel<GroupCard>> response) {
                RspModel<GroupCard> rspModel = response.body();
                if (rspModel.success()) {
                    GroupCard groupCard = rspModel.getResult();
                    //唤起进行保存的操作
                    Factory.getGroupCenter().dispatch(groupCard);
                    // 返回数据
                    callback.onDataLoad(groupCard);
                } else {
                    Factory.decodeRspCode(rspModel, callback);
                }
            }

            @Override
            public void onFailure(Call<RspModel<GroupCard>> call, Throwable t) {
                callback.onDataNotAvailable(R.string.data_network_error);
            }
        });
    }

    public static Call search(String content, final DataSource.Callback<List<GroupCard>> callback) {
        RemoteService service = Network.remote();
        Call<RspModel<List<GroupCard>>> call = service.groupSearch(content);

        call.enqueue(new Callback<RspModel<List<GroupCard>>>() {
            @Override
            public void onResponse(Call<RspModel<List<GroupCard>>> call, Response<RspModel<List<GroupCard>>> response) {
                RspModel<List<GroupCard>> rspModel = response.body();
                if (rspModel.success()) {
                    callback.onDataLoad(rspModel.getResult());
                } else {
                    Factory.decodeRspCode(rspModel, callback);
                }
            }

            @Override
            public void onFailure(Call<RspModel<List<GroupCard>>> call, Throwable t) {
                callback.onDataNotAvailable(R.string.data_network_error);
            }
        });
        //把当前调度者返回
        return call;
    }

    //刷新我的群组列表
    public static void refreshGroups() {
        RemoteService service = Network.remote();
        service.groups("").enqueue(new Callback<RspModel<List<GroupCard>>>() {
            @Override
            public void onResponse(Call<RspModel<List<GroupCard>>> call, Response<RspModel<List<GroupCard>>> response) {
                RspModel<List<GroupCard>> rspModel = response.body();
                if (rspModel.success()) {
                    List<GroupCard> groupCards = rspModel.getResult();
                    if (groupCards != null && groupCards.size() > 0) {
                        //进行调度显示
                        Factory.getGroupCenter().dispatch(groupCards.toArray(new GroupCard[0]));
                    }
                }
            }

            @Override
            public void onFailure(Call<RspModel<List<GroupCard>>> call, Throwable t) {

            }
        });
    }

    //获取一个群的成员数量
    public static long getMemberCount(String id) {
        return SQLite.selectCountOf()
                .from(GroupMember.class)
                .where(GroupMember_Table.group_id.eq(id))
                .count();
    }

    //关联查询一个用户和群成员的表，返回一个MemberUserModel表的集合
    public static List<MemberUserModel> getMemberUsers(String id, int count) {
        return SQLite.select(GroupMember_Table.alias.withTable().as("alias"),
                User_Table.id.withTable().as("userId"),
                User_Table.name.withTable().as("name"),
                User_Table.avatar.withTable().as("avatar"))
                .from(GroupMember.class)
                .join(User.class, Join.JoinType.INNER)
                .on(GroupMember_Table.user_id.withTable().eq(User_Table.id.withTable()))
                .where(GroupMember_Table.group_id.withTable().eq(id))
                .orderBy(GroupMember_Table.user_id, true)
                .limit(count)
                .queryCustomList(MemberUserModel.class);
    }

    //从网络去刷新一个群的成员信息
    public static void refreshGroupMember(Group group) {
        RemoteService service = Network.remote();
        service.groupMembers(group.getId()).enqueue(new Callback<RspModel<List<GroupMemberCard>>>() {
            @Override
            public void onResponse(Call<RspModel<List<GroupMemberCard>>> call, Response<RspModel<List<GroupMemberCard>>> response) {
                RspModel<List<GroupMemberCard>> rspModel = response.body();
                if (rspModel.success()) {
                    List<GroupMemberCard> groupMemberCards = rspModel.getResult();
                    if (groupMemberCards  != null && groupMemberCards.size() > 0) {
                        //进行调度显示
                        Factory.getGroupCenter().dispatch(groupMemberCards.toArray(new GroupMemberCard[0]));
                    }
                }
            }

            @Override
            public void onFailure(Call<RspModel<List<GroupMemberCard>>> call, Throwable t) {

            }
        });
    }
}
