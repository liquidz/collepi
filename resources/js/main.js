(function(window, undefined){
	var Collepi = {},
		template = {},
		snb = null,
		my_collection_page = 1,
   		my_history_page = 1;

	// util {{{
	var applyTemplate = function(obj, tmplMap) {
		if ($.isArray(obj)) {
			$.map(obj, function(v) { applyTemplate(v, tmplMap); });
		} else {
			for (key in tmplMap) {
				obj[key] = snb.bind(tmplMap[key], obj);
			}
		}
	};
	// }}}

	// =getMyCollection
	var getMyCollection = function () {
		$.getJSON("/my/collection", { page: my_collection_page }, function (res) {
			applyTemplate(res, {item_small_image_link: template.ITEM_SMALL_IMAGE_LINK});
			$("#my_collection ul").html(snb.bind_rowset(template.MY_COLLECTION, res));
		});
	};

	// =getMyHistory
	var getMyHistory = function () {
		$.getJSON("/my/history", { page: my_history_page }, function (res) {
			$.map(res, function (v) { v.comment_class = (v.comment !== null) ? "has_comment" : "no_comment"; });
			applyTemplate(res, { item_title_link: template.ITEM_TITLE_LINK });
			$("#my_history ul").html(snb.bind_rowset(template.MY_HISTORY, res));
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
				applyTemplate(history, {item_user_link: template.ITEM_USER_LINK});

				res.item_collected_users = snb.bind_rowset(template.ITEM_COLLECTED_USERS, res.collection);
				res.item_comment = snb.bind_rowset(template.ITEM_COMMENT, history);

				$("#subscreen #item").html(snb.bind(template.ITEM, res));
			});
		} else if(type === "#user"){
			console.log("get user");
		}
	};

	var getRecentCollections = function(){
		$.getJSON("/collection/list", function (res) {
			applyTemplate(res, {
				item_small_image_link: template.ITEM_SMALL_IMAGE_LINK,
				item_title_link: template.ITEM_TITLE_LINK,
				item_user_link: template.ITEM_USER_LINK
			});
			var target = $("#recent_collections ul");
			target.html(snb.bind_rowset(template.RECENT_COLLECTION, res));

			target.find(".js_link").bind("click", Collepi.openJsLink);
		});
	};

	var getRecentComments = function(){
		$.getJSON("/comment/list", function (res) {
			applyTemplate(res, {item_title_link: template.ITEM_TITLE_LINK});
			$("#recent_comments ul").html(snb.bind_rowset(template.RECENT_COMMENT, res));
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
				if(res){ getMyCollection(); }
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
				$("#login").html(snb.bind(tmpl[(res.loggedin) ? "LOGGED_IN" : "NOT_LOGGED_IN"], res));
				if(res.loggedin){
					$(".if_logged_in").show();
					getMyCollection();
					getMyHistory();
				}
			});

			getRecentCollections();
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
