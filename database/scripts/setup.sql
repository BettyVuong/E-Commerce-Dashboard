use template_db;

create table if not exists note
(
    id int auto_increment comment 'Primary Key'
        primary key,
    text varchar(255) null
);
