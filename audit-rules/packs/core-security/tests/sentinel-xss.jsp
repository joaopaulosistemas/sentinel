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
    <!-- ok: sentinel-xss-jsp-unescaped-output -->
    <a href="<%=path%>/login.jsp">Entrar</a>
    <!-- ok: sentinel-xss-jsp-unescaped-output -->
    <a href="<%= request.getContextPath() %>/login.jsp">Entrar</a>
    <!-- ok: sentinel-xss-jsp-unescaped-output -->
    <p><%= esc(titulo) %></p>
    <!-- ruleid: sentinel-xss-jsp-unescaped-output -->
    <p><%= pathInfo + request.getParameter("x") %></p>
    <!-- ruleid: sentinel-xss-jsp-unescaped-output -->
    <p><%= session.getAttribute("user") %></p>
    <!-- ruleid: sentinel-xss-jsp-unescaped-output -->
    <p><%= esc(titulo) + request.getParameter("x") %></p>
    <!-- ok: sentinel-xss-jsp-unescaped-output -->
    <td><%= post.getCommentCount() %></td>
    <!-- ok: sentinel-xss-jsp-unescaped-output -->
    <td><%=(blabber.getNumberListeners() == 1 ? "Ignore" : "Listen")%></td>
    <!-- ruleid: sentinel-xss-jsp-unescaped-output -->
    <td><%= post.getName() %></td>
    <!-- ruleid: sentinel-xss-jsp-unescaped-output -->
    <td><%= (a ? request.getParameter("x") : "b") %></td>
    <!-- ruleid: sentinel-xss-jsp-unescaped-output -->
    <input value="<%=employee.getFirstName()%>">
    <!-- ok: sentinel-xss-jsp-unescaped-output -->
    <input value="<%=GoatHillsFinancial.VIEWPROFILE_ACTION%>">
  </body>
</html>
