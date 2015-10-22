package controllers;

import java.util.concurrent.atomic.AtomicInteger;

import model.domain.Event;
import model.domain.StatusResponse;
import model.domain.User;
import model.domain.WsMessage;
import model.handlers.HandlerPool;
import model.handlers.SimpleWsOutPool;
import model.repository.UserRepository;
import model.sub.RedisSub;
import model.sub.Subscriber;
import model.sub.WebSocketSub;
import play.Logger;
import play.libs.Json;
import play.mvc.BodyParser;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.WebSocket;
import views.html.index;

import com.fasterxml.jackson.databind.JsonNode;

public class Application extends Controller {

	public static Result index() {
		return ok(index.render("Your new application is ready."));
	}
    public static UserRepository userRepository = UserRepository.getInstance();

	public static AtomicInteger ws = new AtomicInteger(0);

	static {
		//subscribe to the message channel
		Subscriber.subscribeToMessageChannel(new RedisSub());
		Subscriber.subscribeToMessageChannel(new WebSocketSub(SimpleWsOutPool.getInstance()));

	}

	@BodyParser.Of(BodyParser.Json.class)
	public static Result message() {
		JsonNode jsonData = request().body().asJson();
		String message = jsonData.findPath("message").textValue();

		if (message == null) {
			return badRequest("Missing parameter [message]");
		}

		HandlerPool.getInstance().send(jsonData);
		return ok();
	}

	public static WebSocket<JsonNode> wsmessage() {
		return new WebSocket<JsonNode>() {

			// Called when the Websocket Handshake is done.
			public void onReady(WebSocket.In<JsonNode> in, WebSocket.Out<JsonNode> out) {
				in.onClose(() -> {
					Logger.debug("UNREGISTERING WS...");
					SimpleWsOutPool.getInstance().unregister(out);
				});

                in.onMessage(callback ->{
                    JsonNode destination = callback.get("destination");
                   JsonNode body=  callback.get("body");

                    System.out.println(callback.toString());

                    switch (destination.textValue()){
                        case "LOGIN":
                            User user = new User(body.textValue());
                            if(userRepository.add(user)){
                                Logger.debug("USER ADDED TO ONLINE USERS");
                                out.write(Json.toJson(new WsMessage<>(Event.LOGGED_IN, StatusResponse.OK, user.getUsername())));
                            }else{
                                Logger.debug("user with username {} are already online",user.getUsername());
                                out.write(Json.toJson(new WsMessage<>(Event.ERROR, StatusResponse.ERROR, "USER ALREADY ONLINE, TRY OTHER USER")));
                            }
                            break;
                        case "GET_USERS":
                            out.write(Json.toJson(new WsMessage<>(Event.ON_ONLINE_USERS, StatusResponse.OK, userRepository.getAll())));
                        case "START_GAME":

                            break;
                        case "INVITE":

                        break;
                        default:
                            System.out.println("NOTHING TO DO");
                    }

                    System.out.println("MESSAGE="+callback.toString());
                });

				try {
					//Subscribe to Redis channel; to receive a message
					Logger.debug("Im ready ws={}", ws.incrementAndGet());
					SimpleWsOutPool.getInstance().register(out);
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			}
		};
	}
}
