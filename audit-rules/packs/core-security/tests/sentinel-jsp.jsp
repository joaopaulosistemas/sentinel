<%@ page import="java.sql.*" %>
<%
String username = request.getParameter("username");
Statement stmt = conn.createStatement();
// ruleid: sentinel-jsp-sql-concatenado
ResultSet rs = stmt.executeQuery("SELECT * FROM Users WHERE name = '" + username + "'");
// ruleid: sentinel-jsp-sql-concatenado
stmt.execute("UPDATE Users SET currentbasketid = " + basketId + " WHERE userid = " + userid);
// ruleid: sentinel-jsp-sql-concatenado
String sql = "SELECT PRODUCT FROM PRODUCTS " +
             "WHERE PRODUCT LIKE '%" + query + "%'";
// ok: sentinel-jsp-sql-concatenado
stmt.execute("UPDATE Score SET status = 1 WHERE task = 'LOGIN_TEST'");
// ok: sentinel-jsp-sql-concatenado
stmt = conn.prepareStatement("SELECT * FROM Products WHERE typeid=" + Integer.parseInt(typeId));
// ok: sentinel-jsp-sql-concatenado
c.prepareStatement("INSERT INTO Users (name) VALUES ('" + getRndPassword() + "')").execute();
// ok: sentinel-jsp-sql-concatenado
PreparedStatement ps = conn.prepareStatement("SELECT * FROM Users WHERE name = ?");
// ok: sentinel-jsp-sql-concatenado
String msg = "Selecione " + "um item";

// ruleid: sentinel-jsp-saida-com-variavel
out.println("<br/>You have logged in successfully: " + username);
// ruleid: sentinel-jsp-saida-com-variavel
out.println("<td>" + rs.getString("name") + "</td><td>" + rs.getString("comment") + "</td>");
// ruleid: sentinel-jsp-saida-com-variavel
out.println(rs.getString("desc"));
// ok: sentinel-jsp-saida-com-variavel
out.println("<td>" + rs.getInt("userid") + "</td>");
// ok: sentinel-jsp-saida-com-variavel
out.println("<td>" + StringEscapeUtils.escapeHtml4(comments) + "</td>");
// ok: sentinel-jsp-saida-com-variavel
out.print("<script src=\""+path+"/jquery.min.js\" type=\"text/javascript\"></script>");
// ok: sentinel-jsp-saida-com-variavel
out.println("<td>" + System.getProperty("os.name") + "</td>");
// ok: sentinel-jsp-saida-com-variavel
out.println("<p>texto fixo</p>");
// ok: sentinel-jsp-saida-com-variavel
out.println("<p>" + "a" + "b" + "</p>");

// ruleid: sentinel-jsp-excecao-na-resposta
out.println("System error.<br/><br/>" + e);
// ruleid: sentinel-jsp-excecao-na-resposta
out.println("DEBUG System error: " + e + "<br/><br/>");
// ruleid: sentinel-jsp-excecao-na-resposta
out.println("Falha: " + ex.getMessage());
// ok: sentinel-jsp-excecao-na-resposta
out.println("System error.");

// ruleid: sentinel-jsp-cookie-sem-httponly
response.addCookie(new Cookie("b_id", basketId));
// ok: sentinel-jsp-cookie-sem-httponly
Cookie c = new Cookie("b_id", basketId); c.setHttpOnly(true); response.addCookie(c);
%>
<h3>Exemplo de injeção</h3>
<!-- ok: sentinel-jsp-sql-concatenado -->
<pre>String q = "SELECT * FROM Users WHERE name = '" + nome + "'";</pre>
