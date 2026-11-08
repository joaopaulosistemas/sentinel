# ruleid: sentinel-tf-s3-acl-publica
resource "aws_s3_bucket" "publico" {
  bucket = "app-publico"
  acl    = "public-read"
}

# ok: sentinel-tf-s3-acl-publica
resource "aws_s3_bucket" "privado" {
  bucket = "app-privado"
  acl    = "private"
}

# ruleid: sentinel-tf-sg-aberto-ao-mundo
resource "aws_security_group" "aberto" {
  name = "aberto"
  ingress {
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

# ok: sentinel-tf-sg-aberto-ao-mundo
resource "aws_security_group" "restrito" {
  name = "restrito"
  ingress {
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = ["10.0.0.0/16"]
  }
}

# ruleid: sentinel-tf-rds-sem-criptografia
resource "aws_db_instance" "sem_cripto" {
  engine            = "postgres"
  storage_encrypted = false
}

# ok: sentinel-tf-rds-sem-criptografia
resource "aws_db_instance" "com_cripto" {
  engine            = "postgres"
  storage_encrypted = true
}

# ruleid: sentinel-tf-senha-no-codigo
resource "aws_db_instance" "senha_fixa" {
  engine   = "postgres"
  password = "hunter2"
}

# ok: sentinel-tf-senha-no-codigo
resource "aws_db_instance" "senha_variavel" {
  engine   = "postgres"
  password = var.db_password
}
