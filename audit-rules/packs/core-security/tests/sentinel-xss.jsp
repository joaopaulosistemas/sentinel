<html>
  <body>
    <!-- ruleid: sentinel-xss-jsp-unescaped-output -->
    <p><%= request.getParameter("nome") %></p>
    <!-- ok: sentinel-xss-jsp-unescaped-output -->
    <p><c:out value="${param.nome}" /></p>
    <!-- ok: sentinel-xss-jsp-unescaped-output -->
    <p><%= bundle.getString("paragraph.info.1") %></p>
    <!-- ok: sentinel-xss-jsp-unescaped-output -->
    <p><%= Encode.forHtml(request.getParameter("nome")) %></p>
    <!-- ok: sentinel-xss-jsp-unescaped-output -->
    <p><%= org.apache.commons.lang3.StringEscapeUtils.escapeHtml4(nome) %></p>
    <!-- ruleid: sentinel-xss-jsp-unescaped-output -->
    <p><%= levelName + request.getParameter("x") %></p>
    <!-- ruleid: sentinel-xss-jsp-unescaped-output -->
    <p><%= rs.getString("nome") %></p>
  </body>
</html>
