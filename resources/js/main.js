
var Osuhon = {};

Osuhon.isbnCount = 0;

Osuhon.loadMyRanks = function(limit, page){
	var params = {};

	if(limit && page){
		console.log("setting");
		params = {limit: limit, page: page};
	}

	$.getJSON("/my", params, function(data){
		$("#myranks ul").html("");
		Osuhon.isbnCount = data.result.length;

		if(data.count <= 3){
			$("#moremyranks").hide();
		}

		$.each(data.result, function(i, v){
			$("#myranks ul").append("<li><img src='"+v.book.thumbnail[2]+"' />" + v.book.isbn +"___"+v.book.title + " - " + v.book.author + "</li>");
		});
	});
};

$(function(){
	$.getJSON("/check/login", function(res){
		if(res.loggedin){
			$("#login").html("<img src='"+res.avatar+"' />" + res.nickname + "<a href='"+res.url+"'>logout</a>");

			Osuhon.loadMyRanks();


		} else {
			$("#login").html("<a href='"+res.url+"'>login</a>");
		}
	});


	$("#add_isbn").bind("click", function(){
		$.get("/set", {isbn: $("#isbn").val(), rank: Osuhon.isbnCount}, function(res){
			Osuhon.loadMyRanks();
		});
	});

	$("#moremyranks").bind("click", function(){
		console.log("kiteru?");
		Osuhon.loadMyRanks(10, 1);
	});

});
