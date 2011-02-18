(function(window, undefined){
	var Collepi = {};
	Collepi.tmpl = {};

	Collepi.updateMyCollection = function(){
		$.getJSON("/my/collection", function(res){
			$("#user_collection ul").html(SNBinder.bind_rowset(Collepi.tmpl.user_collection, res));
		});
	};

	Collepi.updateMyHistory = function(){
		$.getJSON("/my/history", function(res){
			$("#user_history ul").html(SNBinder.bind_rowset(Collepi.tmpl.user_history, res));
		});
	};

	Collepi.getMessage = function(){
		$.getJSON("/message", function(res){
			console.log("message res = " + res);
			$("#message").html(res);
		});
	};

	$(function(){
		SNBinder.init({});

		SNBinder.get_named_sections("/static/template.htm", null, function(tmpl){
			Collepi.tmpl = tmpl;

			$.getJSON("/check/login", function(res){
				$("#login").html(SNBinder.bind(tmpl[(res.loggedin) ? "logged_in" : "not_logged_in"], res));
			});

			Collepi.updateMyCollection();
			Collepi.updateMyHistory();
		});

		$("#add_isbn").bind("click", function(){
			console.log("clicked");
			var isbn = $("#isbn").val();
			$.ajax({
				type: "POST",
				url: "/update/collection",
				data: {
					isbn: isbn,
					read: (($("#read:checked").length === 1) ? "true" : "false"),
					comment: $("#comment").text()
				},
				dataType: "json",
				success: function(res){
					console.log("res = " + res);
					if(res){ Collepi.updateMyCollection(); }
				},
				complete: function(){
					console.log("complete");
					Collepi.getMessage();
				}
			});
		});

		$("#test_btn").bind("click", function(){
			console.log("read = " + $("#read").val());
			console.log("read checked = " + $("#read:checked").length);
		});

		Collepi.getMessage();
	});

	window.Collepi = Collepi;

})(window);
