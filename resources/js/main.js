(function(window, undefined){
	var Collepi = {};
	Collepi.tmpl = {};

	var template = {};
	var snb = null;

	Collepi.myCollectionPage = Collepi.myHistoryPage = 1;

	// util {{{
	var applyTemplate = function(obj, tmplMap){
		if($.isArray(obj)){
			$.map(obj, function(v){ applyTemplate(v, tmplMap); });
		} else {
			for(key in tmplMap){
				obj[key] = snb.bind(tmplMap[key], obj);
			}
		}
	};
	// }}}

	// =getMyCollection
	Collepi.getMyCollection = function(){
		$.getJSON("/my/collection", function(res){
			$("#user_collection ul").html(snb.bind_rowset(Collepi.tmpl.user_collection, res));
		});
	};

	// =getMyHistory
	Collepi.getMyHistory = function(){
		$.getJSON("/my/history", function(res){
			$.map(res, function(v){
				v.comment_class = (v.comment !== null) ? "has_comment" : "no_comment";
			});
			$("#user_history ul").html(snb.bind_rowset(Collepi.tmpl.user_history, res));
		});
	};

	Collepi.openJsLink = function(e){
		var link = $(e.target);
		var href = link.attr("href").split(/\//);
		var type = href[0], val = href[1];

		if(type === "#item"){
			console.log("isbn = " + val);
			$.getJSON("/item", {isbn: val}, function(res){
				var history = $.map(res.history, function(v){ return((v.comment === null) ? null : v); });
				applyTemplate(history, {item_user_link: template.item_user_link});

				res.item_collected_users = snb.bind_rowset(template.item_collected_users, res.collection);
				res.item_comments = snb.bind_rowset(template.item_comments, history);

				$("#subscreen #item").html(snb.bind(template.item, res));
			});
		} else if(type === "#user"){
			console.log("get user");
		}
	};

	Collepi.getRecentCollections = function(){
		$.getJSON("/collection/list", function(res){
			applyTemplate(res, {
				item_small_image_link: template.item_small_image_link,
				item_title_link: template.item_title_link,
				item_user_link: template.item_user_link
			});
			var target = $("#recent_collections ul");
			target.html(snb.bind_rowset(template.recent_collection, res));

			target.find(".js_link").bind("click", Collepi.openJsLink);
		});
	};

	Collepi.getRecentComments = function(){
		$.getJSON("/comment/list", function(res){
			applyTemplate(res, {item_title_link: template.item_title_link});
			$("#recent_comments ul").html(snb.bind_rowset(template.recent_comment, res));
		});
	};

	// =updateCollection
	Collepi.updateCollection = function(isbn){
		$.ajax({
			type: "POST",
			url: "/update/collection",
			data: {
				isbn: isbn,
				read: (($("#read:checked").length === 1) ? "true" : "false"),
				comment: $("#comment").val()
			},
			dataType: "json",
			success: function(res){
				console.log("res = " + res);
				if(res){ Collepi.getMyCollection(); }
			},
			complete: function(){
				//Collepi.getMessage();
				getMessage();
			}
		});
	};

	// =getMessage
	var getMessage = function(){
		$.getJSON("/message", function(res){
			$("#message").html(res);
		});
	};

	$(function(){
		SNBinder.init({});
		snb = SNBinder;

		snb.get_named_sections("/static/template.htm", null, function(tmpl){
			template = tmpl;

			$.getJSON("/check/login", function(res){
				$("#login").html(snb.bind(tmpl[(res.loggedin) ? "logged_in" : "not_logged_in"], res));
				if(res.loggedin){
					$(".if_logged_in").show();
					Collepi.getMyCollection();
					Collepi.getMyHistory();
				}
			});

			Collepi.getRecentCollections();
			Collepi.getRecentComments();
		});

		$("#add_isbn").bind("click", function(){
			console.log("clicked");
			var isbn = $("#isbn").val();
			Collepi.updateCollection(isbn);
		});

		$("#test_btn").bind("click", function(){
			console.log("read = " + (($("#read:checked").length === 1) ? "true" : "false"));
			console.log("text = " + $("#comment").val());
		});

		$("#home_link").bind("click", function(){
			$("#screen section").hide();
			$("#home").show();
		});

		getMessage();
	});

	window.Collepi = Collepi;

})(window);
