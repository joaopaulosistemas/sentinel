<html>
  <body>
    <!-- ruleid: sentinel-xss-jsp-unescaped-output -->
    <p><%= request.getParameter("nome") %></p>
    <!-- ok: sentinel-xss-jsp-unescaped-output -->
    <p><c:out value="${param.nome}" /></p>
  </body>
</html>
