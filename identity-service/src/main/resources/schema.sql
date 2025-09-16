create table users (
    id varchar(36) primary key ,
    username varchar(255) not null unique,
    password varchar(255) not null,
    email varchar(100) not null unique
);

create table roles(
    role_name varchar(100) primary key ,
    description varchar(255)
);

create table user_roles (
    user_id varchar(36) not null ,
    role_name varchar(100) not null ,
    primary key (user_id, role_name),
    foreign key (user_id) references users(id) on delete cascade ,
    foreign key (role_name) references roles(role_name) on delete cascade
)